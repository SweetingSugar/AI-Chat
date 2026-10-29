package com.example.aichat.file.content;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {
    public String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.AI_FILE_CONTENT_EMPTY, "File content is empty");
            }
            return text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.AI_FILE_PARSE_FAILED, "Unable to parse file content", exception);
        }
    }
}
