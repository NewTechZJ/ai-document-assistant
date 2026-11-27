/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.SequencedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the model's answer: which pages it cited, and whether it said "not found". */
public final class AnswerCitations {

    /** [page 3], [Page 3], [pages 3, 5], [p. 3] -> the numbers inside. */
    private static final Pattern CITATION = Pattern.compile(
            "\\[\\s*(?:pages?|p\\.)\\s*(\\d+(?:\\s*(?:,|and|&)\\s*\\d+)*)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private AnswerCitations() {
    }

    /** Cited page numbers, in order of first appearance, without duplicates. */
    public static SequencedSet<Integer> citedPages(String answer) {
        SequencedSet<Integer> pages = new LinkedHashSet<>();
        if (answer == null) {
            return pages;
        }
        Matcher citation = CITATION.matcher(answer);
        while (citation.find()) {
            Matcher number = NUMBER.matcher(citation.group(1));
            while (number.find()) {
                try {
                    pages.add(Integer.parseInt(number.group()));
                } catch (NumberFormatException ignored) {
                    // absurdly long digit run: not a real page, skip it
                }
            }
        }
        return pages;
    }

    /** True when the answer is the not-found sentence (tolerates curly apostrophe, case and surrounding space). */
    public static boolean isNotFound(String answer) {
        if (answer == null) {
            return false;
        }
        return normalize(answer).startsWith(normalize(PromptBuilder.NOT_FOUND_ANSWER));
    }

    private static String normalize(String text) {
        return text.strip().replace('’', '\'').toLowerCase(Locale.ROOT);
    }
}
