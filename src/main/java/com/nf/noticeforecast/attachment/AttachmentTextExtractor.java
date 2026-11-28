package com.nf.noticeforecast.attachment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** -Xmx256m 운영 환경에서 HWPX 압축 폭탄으로 OOM이 나지 않도록 제한한다. */
    static final int MAX_HWPX_ENTRIES = 200;
    static final int MAX_HWPX_ENTRY_BYTES = 2 * 1024 * 1024;
    static final int MAX_HWPX_TOTAL_BYTES = 8 * 1024 * 1024;
    private static final Pattern HWPX_SECTION = Pattern.compile("^Contents/section(\\d+)\\.xml$");
    private static final Comparator<String> HWPX_SECTION_ORDER = Comparator
            .comparingInt(AttachmentTextExtractor::sectionNumber)
            .thenComparing(Comparator.naturalOrder());

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
        Map<String, String> sections = new TreeMap<>(HWPX_SECTION_ORDER);
        int entries = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_HWPX_ENTRIES) {
                    return null;
                }
                String name = entry.getName();
                if (!name.startsWith("Contents/section") || !name.endsWith(".xml")) {
                    continue;
                }
                byte[] xmlBytes = readLimited(zip, MAX_HWPX_ENTRY_BYTES, MAX_HWPX_TOTAL_BYTES - totalBytes);
                if (xmlBytes == null) {
                    return null;
                }
                totalBytes += xmlBytes.length;
                sections.put(name, parseHwpxSection(new String(xmlBytes, StandardCharsets.UTF_8)));
            }
        }
        StringBuilder text = new StringBuilder();
        for (String sectionText : sections.values()) {
            if (!sectionText.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(sectionText);
            }
        }
        return text.toString();
    }

    private static byte[] readLimited(ZipInputStream zip, int maxEntryBytes, long remainingTotalBytes) throws IOException {
        long limit = Math.min(maxEntryBytes, remainingTotalBytes);
        if (limit < 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long count = 0;
        while ((read = zip.read(buffer)) != -1) {
            count += read;
            if (count > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String parseHwpxSection(String xml) {
        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        StringBuilder text = new StringBuilder();
        Element lastParagraph = null;
        for (Element t : document.getElementsByTag("hp:t")) {
            Element paragraph = t.closest("hp|p");
            if (paragraph != lastParagraph && !text.isEmpty()) {
                text.append('\n');
            }
            lastParagraph = paragraph;
            text.append(t.wholeText());
        }
        return text.toString();
    }

    private static int sectionNumber(String name) {
        Matcher matcher = HWPX_SECTION.matcher(name);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
