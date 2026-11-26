package com.nf.noticeforecast.crawl;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RobotsPolicy {

    private final SchoolSiteClient client;
    private final Map<String, RobotsRules> rulesByOrigin = new HashMap<>();

    public RobotsPolicy(SchoolSiteClient client) {
        this.client = client;
    }

    public boolean allows(String url) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        RobotsRules rules = rulesByOrigin.computeIfAbsent(origin, this::load);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return rules.allows(path);
    }

    private RobotsRules load(String origin) {
        try {
            FetchResult result = client.get(origin + "/robots.txt");
            if (result.ok()) {
                return RobotsRules.parse(result.body());
            }
            if (result.status() == 404) {
                return RobotsRules.allowAll();
            }
            log.warn("robots.txt {} returned {}, skipping site", origin, result.status());
        } catch (IOException e) {
            log.warn("robots.txt {} failed, skipping site: {}", origin, e.getMessage());
        }
        return RobotsRules.denyAll();
    }
}
