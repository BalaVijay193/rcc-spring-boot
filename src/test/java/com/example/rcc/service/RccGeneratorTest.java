package com.example.rcc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rcc.model.ExtractedPdf;
import java.util.List;
import org.junit.jupiter.api.Test;

class RccGeneratorTest {
    @Test
    void generatesRoutesAndConflictsFromParsedModel() {
        String text = """
                L-5 PROP. LOADING LINE SH17, SH19 L5 SH2 1CXT CSR 810.00m P-59A 59AXT DE17
                L-4 PROP. LOADING LINE SH17, SH19 L4 SH2 1BXT CSR 810.00m P-59A 60AXT DE18
                """;
        var extracted = new ExtractedPdf("sample.pdf", 1, text, List.of());
        var model = new SipParser().parse(extracted);

        var response = new RccGenerator().generate(extracted, model);

        assertThat(response.routes()).isNotEmpty();
        assertThat(response.routes())
                .anySatisfy(route -> {
                    assertThat(route.fromSignal()).isEqualTo("SH-17");
                    assertThat(route.pointsReverse()).contains("P-59A");
                    assertThat(route.conflictingRoutes()).isNotEmpty();
                });
    }

    @Test
    void reportsScannedPdfWhenThereIsNoTextLayer() {
        var extracted = new ExtractedPdf("scan.pdf", 1, "", List.of());
        var model = new SipParser().parse(extracted);

        var response = new RccGenerator().generate(extracted, model);

        assertThat(response.extractionStatus()).isEqualTo("NO_TEXT_LAYER");
        assertThat(response.warnings())
                .anyMatch(warning -> warning.contains("no extractable text layer"));
        assertThat(response.warnings())
                .anyMatch(warning -> warning.contains("Tesseract OCR"));
        assertThat(response.warnings())
                .noneMatch(warning -> warning.contains("No L-n yard line"));
        assertThat(response.warnings())
                .noneMatch(warning -> warning.contains("No signal labels"));
        assertThat(response.routes()).isEmpty();
    }
}
