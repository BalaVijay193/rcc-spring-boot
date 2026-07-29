package com.example.rcc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SuppliedPdfSmokeTest {
    private static final Path MARUTI_SIP = Path.of("D:/STSWorkspace/SPI and RCC smaple/52. Maruti Yard SIP_08.03.2025.pdf");
    private static final Path MARUTI_SELECTION = Path.of("D:/STSWorkspace/SPI and RCC smaple/51. Selection Table_MSIl Yard_08.03.2025.pdf");
    private static final Path TIKEKARWADI_SIP = Path.of("D:/STSWorkspace/SPI and RCC smaple/Tikekarwadi_SIP_pdf (1).pdf");
    private static final Path TIKEKARWADI_RCC = Path.of("D:/STSWorkspace/SPI and RCC smaple/TIKEKARWADI-RCC  ADV APPD & STATION LAYLOUT PLAN IN CONNECTION WITH E.I & EXTENSION OF LOOP LINES..PDF");

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final SipParser sipParser = new SipParser();
    private final RccTableParser tableParser = new RccTableParser();
    private final RccGenerator generator = new RccGenerator();

    @Test
    void parsesSuppliedMarutiSipAndSelectionTable() throws IOException {
        assumeTrue(Files.exists(MARUTI_SIP) && Files.exists(MARUTI_SELECTION));

        var sip = extract(MARUTI_SIP);
        var selection = extract(MARUTI_SELECTION);
        var yard = sipParser.parse(sip);
        var routes = tableParser.parseRoutes(selection);
        var response = generator.generateFromReference(
                sip,
                yard,
                selection,
                routes,
                tableParser.parseSignals(selection),
                tableParser.parsePoints(selection),
                tableParser.parseTrackCircuits(selection)
        );

        assertThat(yard.lines()).isNotEmpty();
        assertThat(response.routes()).isNotEmpty();
        assertThat(response.extractionStatus()).isEqualTo("REFERENCE_RCC_EXTRACTED");
    }

    @Test
    void parsesSuppliedTikekarwadiRccEvenWhenSipIsScanned() throws IOException {
        assumeTrue(Files.exists(TIKEKARWADI_SIP) && Files.exists(TIKEKARWADI_RCC));

        var sip = extract(TIKEKARWADI_SIP);
        var reference = extract(TIKEKARWADI_RCC);
        var yard = sipParser.parse(sip);
        var routes = tableParser.parseRoutes(reference);
        var response = generator.generateFromReference(
                sip,
                yard,
                reference,
                routes,
                tableParser.parseSignals(reference),
                tableParser.parsePoints(reference),
                tableParser.parseTrackCircuits(reference)
        );

        assertThat(sip.text()).isBlank();
        assertThat(response.routes()).isNotEmpty();
        assertThat(response.extractionStatus()).isEqualTo("REFERENCE_RCC_EXTRACTED");
    }

    private com.example.rcc.model.ExtractedPdf extract(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return extractor.extract(input, path.getFileName().toString());
        }
    }
}
