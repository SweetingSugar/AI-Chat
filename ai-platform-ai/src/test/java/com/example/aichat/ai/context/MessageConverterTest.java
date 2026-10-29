package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageContentType;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageConverterTest {
    @Test
    void preservesLegacyTextMessageConversion() {
        List<ChatMessage> result = MessageConverter.toLangChain4j(
                List.of(new AiMessageDTO(AiMessageRole.USER, "hello")));

        assertThat(result).singleElement().isInstanceOf(UserMessage.class);
        UserMessage user = (UserMessage) result.get(0);
        assertThat(user.singleText()).isEqualTo("hello");
    }

    @Test
    void convertsImageBytesToBase64ImageContent() {
        AiMessageDTO message = new AiMessageDTO(
                AiMessageRole.USER,
                "describe it",
                List.of(AiMessageContentDTO.image("image/png", new byte[]{1, 2, 3})));

        UserMessage user = (UserMessage) MessageConverter.toLangChain4j(List.of(message)).get(0);

        assertThat(user.contents()).hasSize(2);
        assertThat(user.contents().get(0)).isInstanceOf(TextContent.class);
        ImageContent imageContent = (ImageContent) user.contents().get(1);
        assertThat(imageContent.image().mimeType()).isEqualTo("image/png");
        assertThat(imageContent.image().base64Data())
                .isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
    }

    @Test
    void convertsExtractedTextContentBeforeImageContent() {
        AiMessageDTO message = new AiMessageDTO(
                AiMessageRole.USER,
                "question",
                List.of(AiMessageContentDTO.text("extracted"),
                        new AiMessageContentDTO(AiMessageContentType.IMAGE, null, "image/jpeg", new byte[]{4})));

        UserMessage user = (UserMessage) MessageConverter.toLangChain4j(List.of(message)).get(0);

        assertThat(user.contents()).extracting(Object::getClass)
                .containsExactly(TextContent.class, TextContent.class, ImageContent.class);
    }
}
