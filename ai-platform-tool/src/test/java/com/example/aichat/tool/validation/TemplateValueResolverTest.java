package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateValueResolverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateValueResolver resolver = new TemplateValueResolver(objectMapper);

    @Test
    void replacesNestedValuesAndPreservesExactValueTypes() throws Exception {
        JsonNode template = objectMapper.readTree("{\"orderId\":\"{{orderId}}\",\"customerId\":\"{{customer.id}}\",\"label\":\"order-{{orderId}}\"}");

        JsonNode result = resolver.resolve(template, Map.of(
                "orderId", "A-1",
                "customer", Map.of("id", 42)));

        assertThat(result.get("orderId").asText()).isEqualTo("A-1");
        assertThat(result.get("customerId").asInt()).isEqualTo(42);
        assertThat(result.get("label").asText()).isEqualTo("order-A-1");
    }

    @Test
    void resolvesArraysAndNestedObjects() throws Exception {
        JsonNode template = objectMapper.readTree("{\"items\":[\"{{items.0.name}}\",\"{{items.1.name}}\"]}");

        JsonNode result = resolver.resolve(template, Map.of(
                "items", List.of(Map.of("name", "first"), Map.of("name", "second"))));

        assertThat(result.get("items")).extracting(JsonNode::asText)
                .containsExactly("first", "second");
    }

    @Test
    void rejectsMissingTemplateArguments() throws Exception {
        JsonNode template = objectMapper.readTree("{\"city\":\"{{city}}\"}");

        assertThatThrownBy(() -> resolver.resolve(template, Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_ARGUMENT_INVALID);
    }
}
