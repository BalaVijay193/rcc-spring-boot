# RCC Generator

Spring Boot application that accepts a Signal Interlocking Plan PDF and generates a draft Route Control Chart in tabular form.

The current implementation is designed around the attached Maruti Yard SIP sample. It extracts text and positioned labels from the PDF, identifies yard lines, signals, points, axle counters, dead ends and platforms, then creates draft RCC rows with inferred conflicts. Point positions and routes are flagged as `DRAFT_REVIEW` because final EI/RCC data must be validated by a signalling engineer against the authoritative SIP and circuit documents.

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

Upload a text/searchable SIP:

```text
D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf
```

For a scanned/image-only SIP, also upload its approved RCC/Selection Table PDF in the optional `RCC/Table` field:

```text
D:\STSWorkspace\SPI and RCC smaple\Tikekarwadi_SIP_pdf (1).pdf
D:\STSWorkspace\SPI and RCC smaple\TIKEKARWADI-RCC  ADV APPD & STATION LAYLOUT PLAN IN CONNECTION WITH E.I & EXTENSION OF LOOP LINES..PDF
```

## API

Generate JSON:

```powershell
curl.exe -F "file=@D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf" http://localhost:8080/api/rcc
```

Generate JSON from a SIP plus known RCC/Selection Table:

```powershell
curl.exe -F "file=@D:\STSWorkspace\SPI and RCC smaple\Tikekarwadi_SIP_pdf (1).pdf" -F "referenceRcc=@D:\STSWorkspace\SPI and RCC smaple\TIKEKARWADI-RCC  ADV APPD & STATION LAYLOUT PLAN IN CONNECTION WITH E.I & EXTENSION OF LOOP LINES..PDF" http://localhost:8091/api/rcc
```

Generate CSV:

```powershell
curl.exe -F "file=@D:\STSWorkspace\SPI and RCC smaple\52. Maruti Yard SIP_08.03.2025.pdf" http://localhost:8080/api/rcc.csv -o maruti-rcc.csv
```

## Extraction Approach

The route generation follows the thesis idea that yard routes are enumerated between successive signals while carrying the currently active previous signal through track/point traversal. In this implementation:

1. PDFBox extracts sorted text and positioned tokens from the SIP PDF.
2. `SipParser` builds a lightweight yard model from line descriptions such as `L-5`, object labels such as `SH-17`, `P-59A`, `59AXT`, `DE17`, and informative text like platforms/dead ends.
3. `RccTableParser` can read an optional RCC/Selection Table PDF and extract table rows across Maruti-style selection tables and Tikekarwadi-style RCC rows.
4. `RccGenerator` either creates route rows bounded by signals associated with each yard line or returns rows extracted from the supplied RCC/Selection Table, attaching conflicts when rows share signals, points, or track sections.
5. The output is intentionally marked as review data, not a safety-certified RCC.
