package com.example.aichat.knowledge.content;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeTextNormalizer {
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }
}
