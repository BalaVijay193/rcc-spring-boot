package com.example.rcc.api;

import com.example.rcc.model.RccResponse;
import com.example.rcc.service.CsvExporter;
import com.example.rcc.service.PdfTextExtractor;
import com.example.rcc.service.RccGenerator;
import com.example.rcc.service.SipParser;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class RccController {
    private final PdfTextExtractor pdfTextExtractor;
    private final SipParser sipParser;
    private final RccGenerator rccGenerator;
    private final CsvExporter csvExporter;

    public RccController(PdfTextExtractor pdfTextExtractor, SipParser sipParser,
            RccGenerator rccGenerator, CsvExporter csvExporter) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.sipParser = sipParser;
        this.rccGenerator = rccGenerator;
        this.csvExporter = csvExporter;
    }

    @PostMapping(path = "/rcc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RccResponse generate(@RequestParam("file") MultipartFile file) throws IOException {
        var extracted = pdfTextExtractor.extract(file.getInputStream(), file.getOriginalFilename());
        var yardModel = sipParser.parse(extracted);
        return rccGenerator.generate(extracted, yardModel);
    }

    @PostMapping(path = "/rcc.csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> generateCsv(@RequestParam("file") MultipartFile file) throws IOException {
        RccResponse response = generate(file);
        String csv = csvExporter.toCsv(response.routes());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("route-control-chart.csv").build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
