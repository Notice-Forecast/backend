package com.nf.noticeforecast.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.attachment.AttachmentTextExtractor.Format;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class AttachmentTextExtractorTest {

    @Test
    void detectsFormatByFileName() {
        assertThat(AttachmentTextExtractor.formatOf("공고문.PDF", null)).isEqualTo(Format.PDF);
        assertThat(AttachmentTextExtractor.formatOf("신청서.hwp", null)).isEqualTo(Format.HWP);
        assertThat(AttachmentTextExtractor.formatOf("동의서.hwpx", null)).isEqualTo(Format.HWPX);
        assertThat(AttachmentTextExtractor.formatOf("신청서.docx", null)).isEqualTo(Format.UNSUPPORTED);
        assertThat(AttachmentTextExtractor.formatOf("첨부파일.zip", null)).isEqualTo(Format.UNSUPPORTED);
        assertThat(AttachmentTextExtractor.formatOf("download", "application/pdf")).isEqualTo(Format.PDF);
    }

    @Test
    void extractsPdfText() throws Exception {
        assertThat(AttachmentTextExtractor.extract(Format.PDF, pdf("Age 15 to 34")))
                .hasValueSatisfying(text -> assertThat(text).contains("Age 15 to 34"));
    }

    @Test
    void extractsHwpxParagraphs() throws Exception {
        String section = """
                <?xml version="1.0" encoding="UTF-8"?>
                <hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section" xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
                  <hp:p><hp:run><hp:t>지원자격: </hp:t></hp:run><hp:run><hp:t>3·4학년 재학생</hp:t></hp:run></hp:p>
                  <hp:p><hp:run><hp:t>평점 3.0 이상</hp:t></hp:run></hp:p>
                </hs:sec>
                """;

        assertThat(AttachmentTextExtractor.extract(Format.HWPX, zip("Contents/section0.xml", section)))
                .hasValue("지원자격: 3·4학년 재학생\n평점 3.0 이상");
    }

    @Test
    void brokenFileGivesNoText() {
        byte[] garbage = "not a document".getBytes(StandardCharsets.UTF_8);

        assertThat(AttachmentTextExtractor.extract(Format.PDF, garbage)).isEmpty();
        assertThat(AttachmentTextExtractor.extract(Format.HWP, garbage)).isEmpty();
        assertThat(AttachmentTextExtractor.extract(Format.HWPX, garbage)).isEmpty();
    }

    static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] zip(String entry, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
