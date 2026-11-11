package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.tool.config.ToolProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {
    @Test
    void rejectsLoopbackAddressesByDefault() {
        UrlSafetyValidator validator = new UrlSafetyValidator(properties(false));

        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/api"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    }

    @Test
    void rejectsNonHttpSchemes() {
        UrlSafetyValidator validator = new UrlSafetyValidator(properties(false));

        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    }

    @Test
    void permitsPrivateAddressesOnlyWhenExplicitlyEnabled() {
        UrlSafetyValidator validator = new UrlSafetyValidator(properties(true));

        assertThat(validator.validate("http://10.0.0.1/api").getHost()).isEqualTo("10.0.0.1");
    }

    @Test
    void rejectsUrlsWithEmbeddedCredentials() {
        UrlSafetyValidator validator = new UrlSafetyValidator(properties(true));

        assertThatThrownBy(() -> validator.validate("https://user:password@example.com/api"))
                .isInstanceOf(BusinessException.class);
    }

    private ToolProperties properties(boolean allowPrivateNetwork) {
        ToolProperties properties = new ToolProperties();
        properties.setAllowPrivateNetwork(allowPrivateNetwork);
        return properties;
    }
}
