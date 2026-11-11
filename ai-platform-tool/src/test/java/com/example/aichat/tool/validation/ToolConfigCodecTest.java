package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.enums.ToolHttpMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolConfigCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolConfigCodec codec = new ToolConfigCodec(objectMapper);

    @Test
    void roundTripsStructuredHttpConfiguration() {
        JsonNode body = objectMapper.createObjectNode().put("orderId", "{{orderId}}");
        HttpToolConfig config = new HttpToolConfig(
                "https://example.com/orders",
                ToolHttpMethod.POST,
                java.util.Map.of("Accept", "application/json"),
                java.util.Map.of("tenant", "demo"),
                body);

        HttpToolConfig result = codec.decode(codec.encode(config));

        assertThat(result).isEqualTo(config);
    }

    @Test
    void rejectsMalformedConfigurationJson() {
        assertThatThrownBy(() -> codec.decode("{not-json"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    }
}
