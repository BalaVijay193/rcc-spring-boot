package com.example.rcc.service;

import com.example.rcc.model.ExtractedPdf;
import com.example.rcc.model.TextToken;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {
    public ExtractedPdf extract(InputStream inputStream, String fileName) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            String text = textStripper.getText(document);

            TokenStripper tokenStripper = new TokenStripper();
            tokenStripper.setSortByPosition(true);
            tokenStripper.getText(document);
            List<TextToken> tokens = tokenStripper.tokens().stream()
                    .sorted(Comparator.comparingInt(TextToken::page)
                            .thenComparing(TextToken::y)
                            .thenComparing(TextToken::x))
                    .toList();

            return new ExtractedPdf(fileName, document.getNumberOfPages(), text, tokens);
        }
    }

    private static final class TokenStripper extends PDFTextStripper {
        private final List<TextToken> tokens = new ArrayList<>();

        private TokenStripper() throws IOException {
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) {
            String normalized = TextNormalizer.cleanLabel(string);
            if (normalized.isBlank() || textPositions.isEmpty()) {
                return;
            }
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = 0;
            float maxY = 0;
            for (TextPosition position : textPositions) {
                minX = Math.min(minX, position.getXDirAdj());
                minY = Math.min(minY, position.getYDirAdj());
                maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
                maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());
            }
            tokens.add(new TextToken(normalized, getCurrentPageNo(), minX, minY, maxX - minX, maxY - minY));
        }

        List<TextToken> tokens() {
            return tokens;
        }
    }
}
