package com.example.rcc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rcc.model.ExtractedPdf;
import java.util.List;
import org.junit.jupiter.api.Test;

class SipParserTest {
    private final SipParser parser = new SipParser();

    @Test
    void extractsMarutiStyleObjectsAndLines() {
        String text = """
                L-5 PROP. LOADING LINE 822.230m (FM TO PT) SH17, SH19 L5 SH2 1C X T CSR 810.00m P-59A 59AXT PROP. D/E 120.00m LONG
                L-4 PROP. LOADING LINE 822.230m (FM TO FM) SH17, SH19 L4 SH2 1B X T CSR 810.00m P-60A 60AXT DE17
                SH-3/DS3 P-57 57XT RAIL LEVEL PLATFORM
                """;
        var extracted = new ExtractedPdf("sample.pdf", 1, text, List.of());

        var model = parser.parse(extracted);

        assertThat(model.lines()).hasSize(2);
        assertThat(model.signals()).contains("SH-17", "SH-19", "SH-2", "SH-3/DS3");
        assertThat(model.points()).contains("P-59A", "P-60A", "P-57");
        assertThat(model.axleCounters()).contains("1CXT", "59AXT", "60AXT", "57XT");
        assertThat(model.deadEnds()).contains("DE-17");
        assertThat(model.platforms()).contains("PLATFORM-1");
    }
}
