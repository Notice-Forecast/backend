package com.nf.noticeforecast.crawl;

import java.util.ArrayList;
import java.util.List;

public final class RobotsRules {

    private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), false);
    private static final RobotsRules DENY_ALL = new RobotsRules(List.of(), true);

    private final List<String> disallowed;
    private final boolean denyAll;

    private RobotsRules(List<String> disallowed, boolean denyAll) {
        this.disallowed = disallowed;
        this.denyAll = denyAll;
    }

    public static RobotsRules allowAll() {
        return ALLOW_ALL;
    }

    public static RobotsRules denyAll() {
        return DENY_ALL;
    }

    public static RobotsRules parse(String robotsTxt) {
        List<String> disallowed = new ArrayList<>();
        boolean inAllAgents = false;
        boolean previousWasAgent = false;
        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.replaceFirst("#.*", "").strip();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase();
            String value = line.substring(colon + 1).strip();
            if (key.equals("user-agent")) {
                boolean isAll = value.equals("*");
                inAllAgents = previousWasAgent ? inAllAgents || isAll : isAll;
                previousWasAgent = true;
                continue;
            }
            previousWasAgent = false;
            if (inAllAgents && key.equals("disallow") && !value.isEmpty()) {
                disallowed.add(value);
            }
        }
        return new RobotsRules(List.copyOf(disallowed), false);
    }

    public boolean allows(String path) {
        if (denyAll) {
            return false;
        }
        return disallowed.stream().noneMatch(path::startsWith);
    }
}
