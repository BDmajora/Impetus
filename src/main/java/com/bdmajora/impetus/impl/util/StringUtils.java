package com.bdmajora.impetus.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

public class StringUtils {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // Levenshtein distance: the number of edits (insertion, deletion, substitution) needed to transform one string into another; two rolling rows rather than the full m x n table
    public static int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            current[0] = i;
            char c = s1.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = c == s2.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(previous[j] + 1, Math.min(current[j - 1] + 1, previous[j - 1] + cost));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[n];
    }

    // Options whose name carries every word of the input (by prefix or within maxDistance edits), for the options screen search
    public static <T> List<T> fuzzySearch(Iterable<T> options, String userInput, int maxDistance, Function<T, String> toStringFn) {
        List<T> result = new ArrayList<>();
        String[] targetWords = WHITESPACE.split(userInput.toLowerCase(Locale.ROOT));

        for (T option : options) {
            String[] sentenceWords = WHITESPACE.split(toStringFn.apply(option).toLowerCase(Locale.ROOT));

            boolean containsAllWords = true;
            for (String word : targetWords) {
                if (!containsWord(sentenceWords, word, maxDistance)) {
                    containsAllWords = false;
                    break;
                }
            }
            if (containsAllWords) {
                result.add(option);
            }
        }

        return result;
    }

    // A prefix match is the cheap test, so it runs before the edit distance
    private static boolean containsWord(String[] sentenceWords, String word, int maxDistance) {
        for (String sentenceWord : sentenceWords) {
            if (sentenceWord.startsWith(word) || levenshteinDistance(word, sentenceWord) <= maxDistance) {
                return true;
            }
        }
        return false;
    }
}
