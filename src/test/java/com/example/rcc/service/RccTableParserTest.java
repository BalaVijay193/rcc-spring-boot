package com.example.rcc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rcc.model.ExtractedPdf;
import java.util.List;
import org.junit.jupiter.api.Test;

class RccTableParserTest {
    private final RccTableParser parser = new RccTableParser();

    @Test
    void parsesMarutiSelectionTableRows() {
        String text = """
                1 SH1 STOP SH1 UP - 51 51XT, 21AXT CH1 51AXT SH2-L1, SH2-L2 SH1-UP
                2 SH2 DS13 SH2 L5 52 53, 52XT, 53XT, 57XT CH2 SH1-UP, SH3-A1, SH17-L5, SH19-L5 SH2-L5
                """;

        var routes = parser.parseRoutes(new ExtractedPdf("selection.pdf", 1, text, List.of()));

        assertThat(routes).hasSize(2);
        assertThat(routes.get(0).fromSignal()).isEqualTo("SH-1");
        assertThat(routes.get(0).toSignalOrLine()).contains("STOP");
        assertThat(routes.get(1).trackCircuitsOrAxleCounters()).contains("52XT", "53XT", "57XT");
        assertThat(routes.get(1).conflictingRoutes()).contains("SH17-L5", "SH19-L5");
    }

    @Test
    void parsesTikekarwadiStyleRccRows() {
        String text = """
                S2(1)A DN S2 DL-2. 101 NB 105 A/B 202T 203T 204T 208T SH13(3) S4 ROUTE RELEASE
                S2(2) DN S2 DM 101 A/B 202T 114 A/B 217T 211T S3 CH14
                CO2(1) S2 CH1 DAL 202T 201T COGGN CH5 203T SH13(4) CO2 HG CLEARS
                """;

        var routes = parser.parseRoutes(new ExtractedPdf("tik-rcc.pdf", 1, text, List.of()));

        assertThat(routes).hasSize(3);
        assertThat(routes.get(0).fromSignal()).isEqualTo("S-2(1)A");
        assertThat(routes.get(0).toSignalOrLine()).contains("DL-2");
        assertThat(routes.get(0).pointsNormal()).contains("101 NB", "105 A/B");
        assertThat(routes.get(2).fromSignal()).isEqualTo("CO-2(1)");
    }
}
