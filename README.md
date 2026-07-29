# RCC Generator

Spring Boot application that accepts a Signal Interlocking Plan PDF and generates a draft Route Control Chart in tabular form.

The application processes the SIP itself. It extracts text and positioned labels from the SIP PDF, identifies yard lines, signals, points, axle counters, dead ends and platforms, then creates draft RCC rows with inferred conflicts. If a SIP is scanned/image-only, the app automatically tries local Tesseract OCR before parsing. Point positions and routes are flagged as `DRAFT_REVIEW` because final EI/RCC data must be validated by a signalling engineer against the authoritative SIP and circuit documents.

## Run

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Sync To STS Workspace

The preferred working copy for this project is:

```text
D:\STSWorkspace\29Jul2026\railway-signaling-diagram-processor
```

From this repository, run:

```powershell
.\sync-to-sts-workspace.ps1
```

Upload a SIP:

```text
D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf
```

For scanned/image-only SIPs, install Tesseract OCR on the machine or provide an OCR/searchable SIP PDF. The UI still accepts only the SIP file.


## API

Generate JSON:

```powershell
curl.exe -F "file=@D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf" http://localhost:8080/api/rcc
```

Generate CSV:

```powershell
curl.exe -F "file=@D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf" http://localhost:8080/api/rcc.csv -o maruti-rcc.csv
```

## Extraction Approach

The route generation follows the thesis idea that yard routes are enumerated between successive signals while carrying the currently active previous signal through track/point traversal. In this implementation:

1. PDFBox extracts sorted text and positioned tokens from the SIP PDF.
2. If the SIP has no text layer, PDFBox renders SIP pages to images and the extractor calls local `tesseract` OCR when available.
3. `SipParser` builds a lightweight yard model from line descriptions such as `L-5`, object labels such as `SH-17`, `P-59A`, `59AXT`, `DE17`, and informative text like platforms/dead ends.
4. `RccGenerator` creates route rows bounded by signals associated with each yard line, attaching conflicts when rows share signals, points, or track sections.
5. The output is intentionally marked as review data, not a safety-certified RCC.
