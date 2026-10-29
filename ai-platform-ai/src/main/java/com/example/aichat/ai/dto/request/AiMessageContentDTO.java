package com.example.aichat.ai.dto.request;

public record AiMessageContentDTO(AiMessageContentType type, String text,
                                  String mediaType, byte[] data) {
    public AiMessageContentDTO {
        if (type == null) throw new IllegalArgumentException("Content type must not be null");
        data = data == null ? null : data.clone();
    }

    public static AiMessageContentDTO text(String value) {
        return new AiMessageContentDTO(AiMessageContentType.TEXT, value, null, null);
    }

    public static AiMessageContentDTO image(String mediaType, byte[] data) {
        return new AiMessageContentDTO(AiMessageContentType.IMAGE, null, mediaType, data);
    }
}
