package com.example.aichat.file.content;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {
    private PdfTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PdfTextExtractor();
    }

    @Test
    void extractsTextFromPdf() throws IOException {
        byte[] pdf = pdfWithText("invoice total 42");

        assertThat(extractor.extract(pdf)).contains("invoice total 42");
    }

    @Test
    void rejectsPdfWithoutExtractedText() throws IOException {
        assertThatThrownBy(() -> extractor.extract(pdfWithoutText()))
                .hasMessage("File content is empty");
    }

    @Test
    void rejectsMalformedPdf() {
        assertThatThrownBy(() -> extractor.extract("not pdf".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("Unable to parse file content");
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithoutText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
