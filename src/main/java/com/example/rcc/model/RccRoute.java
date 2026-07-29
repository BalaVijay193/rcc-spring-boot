package com.example.rcc.model;

import java.util.List;

public record RccRoute(
        String routeId,
        String routeName,
        String fromSignal,
        String toSignalOrLine,
        String direction,
        String routeType,
        List<String> trackCircuitsOrAxleCounters,
        List<String> pointsNormal,
        List<String> pointsReverse,
        List<String> overlapOrIsolation,
        List<String> conflictingRoutes,
        String sourceEvidence,
        String reviewStatus
) {
}
