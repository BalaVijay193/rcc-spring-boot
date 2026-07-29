package com.example.rcc.model;

import java.util.List;
import java.util.Set;

public record RccResponse(
        String fileName,
        int pageCount,
        String extractionStatus,
        Set<String> signals,
        Set<String> points,
        Set<String> axleCounters,
        Set<String> deadEnds,
        List<YardLine> yardLines,
        List<RccRoute> routes,
        List<String> warnings
) {
}
