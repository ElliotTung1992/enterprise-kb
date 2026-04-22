package com.enterprise.kb.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTI_DASH = Pattern.compile("-+");

    private SlugUtils() {}

    public static String toSlug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return MULTI_DASH.matcher(
                NON_LATIN.matcher(
                        WHITESPACE.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-")
                ).replaceAll("")
        ).replaceAll("-").strip();
    }
}
