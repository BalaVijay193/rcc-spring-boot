package com.example.rcc.model;

import java.util.List;
import java.util.Set;

public record YardModel(
        List<YardLine> lines,
        Set<String> signals,
        Set<String> points,
        Set<String> axleCounters,
        Set<String> deadEnds,
        Set<String> platforms,
        List<String> notes
) {
}
