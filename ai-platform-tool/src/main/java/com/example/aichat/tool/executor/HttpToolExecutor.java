package com.example.aichat.tool.executor;

import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.validation.TemplateValueResolver;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpToolExecutor implements ToolExecutor {
    @Override
    public ToolType toolType() {
        return ToolType.HTTP;
    }

    private final RestClient restClient;
    private final ToolProperties properties;
    private final ToolConfigCodec configCodec;
    private final TemplateValueResolver templateValueResolver;
    private final ToolCredentialHeaders credentialHeaders;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ObjectMapper objectMapper;

    public HttpToolExecutor(RestClient.Builder restClientBuilder,
                            ToolProperties properties,
                            ToolConfigCodec configCodec,
                            TemplateValueResolver templateValueResolver,
                            ToolCredentialHeaders credentialHeaders,
                            UrlSafetyValidator urlSafetyValidator,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.configCodec = configCodec;
        this.templateValueResolver = templateValueResolver;
        this.credentialHeaders = credentialHeaders;
        this.urlSafetyValidator = urlSafetyValidator;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getDefaultConnectTimeout()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getDefaultReadTimeout()));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public ToolExecutionResult execute(ToolDefinition definition, ToolExecutionContext context) {
        if (definition.getToolType() != ToolType.HTTP) {
            throw new ToolRemoteException(ErrorCode.TOOL_CONFIG_INVALID, "Tool is not an HTTP tool", null);
        }
        HttpToolConfig config = configCodec.decode(definition.getHttpConfig());
        URI validatedUri = urlSafetyValidator.validate(config.url());
        URI requestUri = addQueryParameters(validatedUri, config.queryParams(), context.arguments());
        Map<String, String> headers = resolveHeaders(config.headers(), context.arguments());
        headers.putAll(credentialHeaders.resolve(definition.getAuthType(), definition.getAuthHeaderName(),
                definition.getCredentialCiphertext(), definition.getSecretHeadersCiphertext()));
        JsonNode body = config.bodyTemplate() == null
                ? null : templateValueResolver.resolve(config.bodyTemplate(), context.arguments());
        long started = System.nanoTime();
        try {
            RestClient.RequestBodySpec request = restClient.method(HttpMethod.valueOf(config.method().name()))
                    .uri(requestUri);
            headers.forEach(request::header);
            if (body != null && !body.isNull()) {
                request.contentType(MediaType.APPLICATION_JSON);
                request.body(body);
            }
            return request.exchange((httpRequest, response) -> {
                long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
                byte[] responseBytes = readResponse(response.getBody());
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new ToolRemoteException(ErrorCode.TOOL_REMOTE_ERROR,
                            "Remote request returned HTTP " + response.getStatusCode().value(), null);
                }
                return new ToolExecutionResult(context.requestId(), definition.getToolCode(), ToolType.HTTP,
                        true, response.getStatusCode().value(), parseResponse(response.getHeaders(), responseBytes), duration);
            });
        } catch (ToolRemoteException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            ErrorCode code = isReadTimeout(exception) ? ErrorCode.TOOL_READ_TIMEOUT : ErrorCode.TOOL_CONNECT_TIMEOUT;
            throw new ToolRemoteException(code, code.message(), exception);
        } catch (Exception exception) {
            throw new ToolRemoteException(ErrorCode.TOOL_REMOTE_ERROR, "HTTP tool request failed", exception);
        }
    }

    private URI addQueryParameters(URI uri, Map<String, String> queryParams, Map<String, Object> arguments) {
        if (queryParams == null || queryParams.isEmpty()) {
            return uri;
        }
        Map<String, String> resolved = resolveHeaders(queryParams, arguments);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
        resolved.forEach(builder::queryParam);
        return builder.build().toUri();
    }

    private Map<String, String> resolveHeaders(Map<String, String> configured, Map<String, Object> arguments) {
        if (configured == null || configured.isEmpty()) {
            return new LinkedHashMap<>();
        }
        JsonNode resolved = templateValueResolver.resolve(objectMapper.valueToTree(configured), arguments);
        Map<String, String> result = new LinkedHashMap<>();
        resolved.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isValueNode()
                ? entry.getValue().asText() : entry.getValue().toString()));
        return result;
    }

    private byte[] readResponse(java.io.InputStream input) {
        try {
            byte[] bytes = input.readNBytes(properties.getMaxResponseBytes() + 1);
            if (bytes.length > properties.getMaxResponseBytes()) {
                throw new ToolRemoteException(ErrorCode.TOOL_REMOTE_ERROR, "HTTP response exceeds configured limit", null);
            }
            return bytes;
        } catch (IOException exception) {
            throw new ToolRemoteException(ErrorCode.TOOL_REMOTE_ERROR, "Unable to read HTTP response", exception);
        }
    }

    private Object parseResponse(HttpHeaders headers, byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        MediaType contentType = headers.getContentType();
        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            try {
                return objectMapper.readTree(bytes);
            } catch (IOException exception) {
                throw new ToolRemoteException(ErrorCode.TOOL_REMOTE_ERROR, "HTTP response JSON is invalid", exception);
            }
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private boolean isReadTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
