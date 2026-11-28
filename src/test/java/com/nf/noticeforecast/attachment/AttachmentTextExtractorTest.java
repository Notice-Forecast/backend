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

    @Test
    void sortsHwpxSectionsNumerically() throws Exception {
        String s2 = "<hs:sec xmlns:hs=\"h\" xmlns:hp=\"p\"><hp:p><hp:t>둘째</hp:t></hp:p></hs:sec>";
        String s10 = "<hs:sec xmlns:hs=\"h\" xmlns:hp=\"p\"><hp:p><hp:t>열째</hp:t></hp:p></hs:sec>";
        byte[] bytes = zipEntries(java.util.Map.of(
                "Contents/section10.xml", s10,
                "Contents/section2.xml", s2));

        assertThat(AttachmentTextExtractor.extract(Format.HWPX, bytes)).hasValue("둘째\n열째");
    }

    @Test
    void rejectsHwpxExceedingUncompressedLimit() throws Exception {
        String huge = "<hs:sec xmlns:hs=\"h\" xmlns:hp=\"p\"><hp:p><hp:t>"
                + "a".repeat(AttachmentTextExtractor.MAX_HWPX_ENTRY_BYTES + 1024)
                + "</hp:t></hp:p></hs:sec>";

        assertThat(AttachmentTextExtractor.extract(Format.HWPX, zip("Contents/section0.xml", huge))).isEmpty();
    }

    private static byte[] zip(String entry, String content) throws Exception {
        return zipEntries(java.util.Map.of(entry, content));
    }

    private static byte[] zipEntries(java.util.Map<String, String> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
