package com.example.rcc.model;

import java.util.List;

public record YardLine(
        String id,
        String description,
        double csrMeters,
        List<String> signals,
        List<String> points,
        List<String> axleCounters,
        List<String> deadEnds
) {
}
