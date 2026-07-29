package com.example.rcc.service;

import com.example.rcc.model.ExtractedPdf;
import com.example.rcc.model.TextToken;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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
            if (TextNormalizer.cleanLabel(text).isBlank()) {
                text = runOcrIfAvailable(document);
            }

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

    private String runOcrIfAvailable(PDDocument document) throws IOException {
        if (!isTesseractAvailable()) {
            return "";
        }
        Path tempDir = Files.createTempDirectory("sip-ocr-");
        StringBuilder text = new StringBuilder();
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageLimit = Math.min(document.getNumberOfPages(), 4);
            for (int page = 0; page < pageLimit; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 250, ImageType.RGB);
                Path imagePath = tempDir.resolve("page-" + (page + 1) + ".png");
                ImageIO.write(image, "png", imagePath.toFile());
                text.append(runTesseract(imagePath));
                text.append('\n');
            }
        } finally {
            deleteQuietly(tempDir);
        }
        return text.toString();
    }

    private static boolean isTesseractAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String runTesseract(Path imagePath) {
        try {
            Process process = new ProcessBuilder("tesseract", imagePath.toString(), "stdout", "--psm", "6")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "";
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {
            // Temporary OCR files are best-effort cleanup.
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
