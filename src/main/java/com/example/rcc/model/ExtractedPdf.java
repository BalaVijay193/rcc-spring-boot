package com.example.rcc.model;

import java.util.List;

public record ExtractedPdf(
        String fileName,
        int pageCount,
        String text,
        List<TextToken> tokens
) {
}
