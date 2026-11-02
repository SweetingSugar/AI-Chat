package com.example.aichat.file.content;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class DocxTextExtractor {
    public String extract(byte[] bytes) {
        try {
            String text = isZip(bytes) ? extractDocx(bytes) : extractDoc(bytes);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.AI_FILE_CONTENT_EMPTY, "File content is empty");
            }
            return text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_FILE_PARSE_FAILED,
                    "Unable to parse office document", exception);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().trim())
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.joining("\n"));
        }
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getRange().text();
        }
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
    }
}
