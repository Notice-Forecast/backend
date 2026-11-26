package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.notice.NoticeAttachment;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

public class NoticeDetailParser {

    private static final Set<String> CELL_TAGS = Set.of("td", "th");
    private static final String CELL_SEPARATOR = " | ";
    private static final Pattern REPEATED_SEPARATORS = Pattern.compile("(?:\\s*\\|\\s*)+");

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "li", "tr", "table", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre");

    public NoticeDetail parse(String html, String viewUrl) {
        Document document = Jsoup.parse(html, viewUrl);
        Element body = document.selectFirst("div.b-con-box");
        String text = body == null ? "" : toPlainText(body);
        int images = body == null ? 0 : body.select("img").size();
        List<NoticeAttachment> attachments = document.select("div.b-file-box a.file-down-btn").stream()
                .map(link -> new NoticeAttachment(link.text().strip(), link.absUrl("href")))
                .filter(attachment -> !attachment.url().isEmpty())
                .toList();
        return new NoticeDetail(text, images, attachments);
    }

    static String toPlainText(Element root) {
        StringBuilder out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    out.append(textNode.text());
                } else if (node instanceof Element element && element.normalName().equals("br")) {
                    out.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element) {
                    if (CELL_TAGS.contains(element.normalName())) {
                        out.append(CELL_SEPARATOR);
                    } else if (BLOCK_TAGS.contains(element.normalName())) {
                        out.append('\n');
                    }
                }
            }
        }, root);
        return out.toString().lines()
                .map(line -> line.replace('\u00a0', ' ').replaceAll("\\s+", " "))
                .map(line -> stripSeparators(REPEATED_SEPARATORS.matcher(line).replaceAll(CELL_SEPARATOR).strip()))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private static String stripSeparators(String line) {
        String result = line;
        while (result.startsWith("|")) {
            result = result.substring(1).strip();
        }
        while (result.endsWith("|")) {
            result = result.substring(0, result.length() - 1).strip();
        }
        return result;
    }
}
