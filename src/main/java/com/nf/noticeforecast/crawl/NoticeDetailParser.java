package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.notice.NoticeAttachment;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

public class NoticeDetailParser {

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
                if (node instanceof Element element && BLOCK_TAGS.contains(element.normalName())) {
                    out.append('\n');
                }
            }
        }, root);
        return out.toString().lines()
                .map(line -> line.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip())
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
