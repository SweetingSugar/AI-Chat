package com.example.aichat.ai.service;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.dto.response.AiProviderManagementDTO;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.mapper.AiProviderMapper;
import com.example.aichat.ai.service.impl.AiProviderServiceImpl;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderServiceImplTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void createsProviderWithEncryptedCredentialAndDoesNotExposeIt() {
        Fixture fixture = new Fixture();

        AiProviderManagementDTO result = fixture.service.create(request("sk-secret"));

        assertThat(fixture.inserted.getApiKeyCiphertext()).startsWith("v1:").isNotEqualTo("sk-secret");
        assertThat(result.hasApiKey()).isTrue();
        assertThat(result.credentialVersion()).isEqualTo(1);
    }

    @Test
    void updateWithoutApiKeyKeepsStoredCredentialAndVersion() {
        Fixture fixture = new Fixture();
        fixture.provider = provider();

        AiProviderManagementDTO result = fixture.service.update(1L, request(" "));

        assertThat(fixture.updated.getApiKeyCiphertext()).isEqualTo("v1:original");
        assertThat(fixture.updated.getCredentialVersion()).isEqualTo(4);
        assertThat(result.hasApiKey()).isTrue();
    }

    @Test
    void rejectsProviderDeletionWhenModelsStillExist() {
        Fixture fixture = new Fixture();
        fixture.provider = provider();
        fixture.modelCount = 1;

        assertThatThrownBy(() -> fixture.service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("models");
    }

    private static AiProviderUpsertRequest request(String apiKey) {
        return new AiProviderUpsertRequest("openai", "OpenAI", AiProviderType.OPENAI,
                "https://api.openai.com/v1", apiKey, 5, 30, 1, 0, "primary");
    }

    private static AiProvider provider() {
        AiProvider provider = new AiProvider();
        provider.setId(1L);
        provider.setProviderCode("openai");
        provider.setProviderName("OpenAI");
        provider.setProviderType(AiProviderType.OPENAI);
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setApiKeyCiphertext("v1:original");
        provider.setCredentialVersion(4);
        provider.setConnectTimeout(5);
        provider.setReadTimeout(30);
        provider.setStatus(1);
        provider.setSort(0);
        return provider;
    }

    private static class Fixture {
        private AiProvider provider;
        private AiProvider inserted;
        private AiProvider updated;
        private long modelCount;
        private final AiProviderServiceImpl service;

        private Fixture() {
            AiProviderMapper providerMapper = proxy(AiProviderMapper.class, (method, arguments) -> switch (method) {
                case "selectCount" -> 0L;
                case "selectById" -> provider;
                case "insert" -> { inserted = (AiProvider) arguments[0]; yield 1; }
                case "updateById" -> { updated = (AiProvider) arguments[0]; yield 1; }
                case "deleteById" -> 1;
                default -> throw new UnsupportedOperationException(method);
            });
            AiModelMapper modelMapper = proxy(AiModelMapper.class, (method, arguments) -> {
                if (method.equals("selectCount")) return modelCount;
                throw new UnsupportedOperationException(method);
            });
            service = new AiProviderServiceImpl(providerMapper, modelMapper, new AiCredentialCipher(KEY));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MapperInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return method.getName().equals("toString") ? type.getSimpleName() : null;
            return invocation.invoke(method.getName(), arguments == null ? new Object[0] : arguments);
        });
    }

    @FunctionalInterface
    private interface MapperInvocation {
        Object invoke(String method, Object[] arguments);
    }
}
