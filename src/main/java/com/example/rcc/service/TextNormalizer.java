package com.example.rcc.service;

import java.text.Normalizer;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String cleanLabel(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ')
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    static String compactObjectName(String value) {
        return cleanLabel(value).toUpperCase()
                .replaceAll("\\s+", "")
                .replace("POINTNO-", "P-")
                .replace("PT.NO-", "P-")
                .replace("PTNO-", "P-")
                .replace("PTNO", "P-")
                .replace("PT.NO", "P-")
                .replace("P.NO", "P-");
    }
}
