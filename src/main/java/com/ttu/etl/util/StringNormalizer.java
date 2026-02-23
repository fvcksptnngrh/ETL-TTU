package com.ttu.etl.util;

import org.springframework.stereotype.Component;

@Component
public class StringNormalizer {

    public String normalize(String input) {
        if (input == null) {
            return null;
        }

        // Trim whitespace
        String normalized = input.trim();

        // Replace multiple spaces with single space
        normalized = normalized.replaceAll("\\s+", " ");

        // Capitalize properly (first letter of each word)
        normalized = capitalizeWords(normalized);

        return normalized;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }
}
