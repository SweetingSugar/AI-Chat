package com.example.aichat.file.content;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTextExtractorTest {
    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void extractsParagraphTextFromDocx() throws Exception {
        byte[] bytes = createDocx("第一段", "第二段");

        assertThat(extractor.extract(bytes)).contains("第一段", "第二段");
    }

    @Test
    void rejectsEmptyDocx() throws Exception {
        assertThatThrownBy(() -> extractor.extract(createDocx()))
                .extracting("errorCode.code")
                .isEqualTo("AI_FILE_CONTENT_EMPTY");
    }

    private byte[] createDocx(String... paragraphs) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
        }
        return output.toByteArray();
    }
}
