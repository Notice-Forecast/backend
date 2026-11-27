package com.nf.noticeforecast.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** PDF는 PDFBox, HWP는 hwplib, HWPX는 압축 안의 본문 XML에서 텍스트를 뽑는다(CLAUDE.md 8.5). */
public final class AttachmentTextExtractor {

    public enum Format { PDF, HWP, HWPX, UNSUPPORTED }

    private AttachmentTextExtractor() {
    }

    public static Format formatOf(String name, String contentType) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT).strip();
        if (lower.endsWith(".pdf") || (contentType != null && contentType.startsWith("application/pdf"))) {
            return Format.PDF;
        }
        if (lower.endsWith(".hwpx")) {
            return Format.HWPX;
        }
        if (lower.endsWith(".hwp")) {
            return Format.HWP;
        }
        return Format.UNSUPPORTED;
    }

    /** @return 뽑은 텍스트. 읽지 못하면 비어 있다 */
    public static Optional<String> extract(Format format, byte[] bytes) {
        try {
            String text = switch (format) {
                case PDF -> pdf(bytes);
                case HWP -> hwp(bytes);
                case HWPX -> hwpx(bytes);
                case UNSUPPORTED -> null;
            };
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.strip());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String hwp(byte[] bytes) throws Exception {
        HWPFile file = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        return TextExtractor.extract(file, TextExtractMethod.InsertControlTextBetweenParagraphText);
    }

    private static String hwpx(byte[] bytes) throws IOException {
        Map<String, String> sections = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("Contents/section") && name.endsWith(".xml")) {
                    sections.put(name, new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (String xml : sections.values()) {
            Document document = Jsoup.parse(xml, "", Parser.xmlParser());
            Element lastParagraph = null;
            for (Element t : document.getElementsByTag("hp:t")) {
                Element paragraph = t.closest("hp|p");
                if (paragraph != lastParagraph && !text.isEmpty()) {
                    text.append('\n');
                }
                lastParagraph = paragraph;
                text.append(t.wholeText());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
