package com.enterprise.kb.document.service.impl;

/**
 * Markdown chunk token 粗估工具。
 */
final class MarkdownTokenEstimator {

    private MarkdownTokenEstimator() {
    }

    static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long cjk = text.chars().filter(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        long nonCjk = text.length() - cjk;
        return Math.max(1, (int) (cjk + Math.ceil(nonCjk / 4.0)));
    }
}
