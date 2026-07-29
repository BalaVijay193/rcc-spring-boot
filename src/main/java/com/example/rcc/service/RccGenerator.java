package com.example.rcc.service;

import com.example.rcc.model.ExtractedPdf;
import com.example.rcc.model.RccResponse;
import com.example.rcc.model.RccRoute;
import com.example.rcc.model.YardLine;
import com.example.rcc.model.YardModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RccGenerator {
    public RccResponse generate(ExtractedPdf extractedPdf, YardModel yardModel) {
        List<RccRoute> routes = buildRoutes(yardModel);
        List<RccRoute> routesWithConflicts = attachConflicts(routes);
        List<String> warnings = new ArrayList<>(yardModel.notes());
        boolean hasTextLayer = !TextNormalizer.cleanLabel(extractedPdf.text()).isBlank();
        if (!hasTextLayer) {
            warnings.add(0, "This PDF has no extractable text layer. It appears to be a scanned/image-only SIP. Run OCR to create a searchable PDF, then upload the OCR PDF.");
        }
        if (routesWithConflicts.isEmpty()) {
            warnings.add("No routes could be generated from extracted yard lines.");
        }
        if (hasTextLayer) {
            warnings.add("Point N/R positions are inferred from extracted line/point membership and require field validation.");
        }

        return new RccResponse(
                extractedPdf.fileName(),
                extractedPdf.pageCount(),
                hasTextLayer ? "DRAFT_REVIEW" : "NO_TEXT_LAYER",
                yardModel.signals(),
                yardModel.points(),
                yardModel.axleCounters(),
                yardModel.deadEnds(),
                yardModel.lines(),
                routesWithConflicts,
                warnings
        );
    }

    public RccResponse generateFromReference(ExtractedPdf sipPdf, YardModel sipModel,
            ExtractedPdf referencePdf, List<RccRoute> referenceRoutes, Set<String> referenceSignals,
            Set<String> referencePoints, Set<String> referenceTrackCircuits) {
        Set<String> signals = new LinkedHashSet<>(sipModel.signals());
        signals.addAll(referenceSignals);
        Set<String> points = new LinkedHashSet<>(sipModel.points());
        points.addAll(referencePoints);
        Set<String> axleCounters = new LinkedHashSet<>(sipModel.axleCounters());
        axleCounters.addAll(referenceTrackCircuits);

        List<String> warnings = new ArrayList<>();
        if (TextNormalizer.cleanLabel(sipPdf.text()).isBlank()) {
            warnings.add("SIP PDF has no extractable text layer; routes were read from the supplied RCC/Selection Table PDF.");
        } else {
            warnings.add("Routes were read from the supplied RCC/Selection Table PDF and SIP labels were used as supporting object evidence.");
        }
        warnings.add("Reference table parsing is generic and must be checked against the approved signalling RCC before EI data use.");
        if (referenceRoutes.isEmpty()) {
            warnings.add("No route rows could be extracted from the supplied RCC/Selection Table PDF.");
        }

        return new RccResponse(
                sipPdf.fileName(),
                sipPdf.pageCount(),
                referenceRoutes.isEmpty() ? "REFERENCE_RCC_NO_ROWS" : "REFERENCE_RCC_EXTRACTED",
                signals,
                points,
                axleCounters,
                sipModel.deadEnds(),
                sipModel.lines(),
                attachConflicts(referenceRoutes),
                warnings
        );
    }

    private List<RccRoute> buildRoutes(YardModel model) {
        List<YardLine> lines = model.lines().stream()
                .sorted(Comparator.comparing(YardLine::id))
                .toList();
        List<RccRoute> routes = new ArrayList<>();
        int sequence = 1;
        for (YardLine line : lines) {
            List<String> entrySignals = entrySignals(line, model.signals());
            String exit = exitSignal(line, model.signals());
            for (String signal : entrySignals) {
                routes.add(route(sequence++, line, signal, exit, "UP", "LINE_ENTRY", model));
            }
            if (!exit.isBlank() && !entrySignals.isEmpty()) {
                routes.add(route(sequence++, line, exit, line.id(), "DOWN", "LINE_EXIT", model));
            }
        }
        return routes;
    }

    private RccRoute route(int sequence, YardLine line, String fromSignal, String toSignal,
            String direction, String routeType, YardModel model) {
        List<String> pointsReverse = line.points().isEmpty()
                ? nearbyPoints(line, model.points())
                : unique(line.points());
        List<String> pointsNormal = model.points().stream()
                .filter(point -> !pointsReverse.contains(point))
                .limit(6)
                .toList();

        List<String> overlap = new ArrayList<>();
        if (!line.deadEnds().isEmpty()) {
            overlap.addAll(line.deadEnds());
        }
        if (line.csrMeters() > 0) {
            overlap.add("CSR " + formatMeters(line.csrMeters()) + "m");
        }

        return new RccRoute(
                "R" + String.format(Locale.ROOT, "%03d", sequence),
                fromSignal + " -> " + toSignal + " via " + line.id(),
                fromSignal,
                toSignal,
                direction,
                routeType,
                line.axleCounters().isEmpty() ? inferredAxleCounters(line, model.axleCounters()) : unique(line.axleCounters()),
                pointsNormal,
                pointsReverse,
                overlap,
                List.of(),
                line.description(),
                "DRAFT_REVIEW"
        );
    }

    private List<String> entrySignals(YardLine line, Set<String> allSignals) {
        List<String> lineSignals = line.signals().stream()
                .filter(signal -> signal.startsWith("SH-"))
                .filter(signal -> !signal.equals("SH-2"))
                .toList();
        if (!lineSignals.isEmpty()) {
            return unique(lineSignals);
        }
        return allSignals.stream()
                .filter(signal -> signal.startsWith("SH-"))
                .filter(signal -> !signal.equals("SH-2"))
                .limit(2)
                .toList();
    }

    private String exitSignal(YardLine line, Set<String> allSignals) {
        if (line.signals().contains("SH-2")) {
            return "SH-2";
        }
        return allSignals.stream()
                .filter(signal -> signal.equals("SH-2"))
                .findFirst()
                .orElse(line.id());
    }

    private List<String> nearbyPoints(YardLine line, Set<String> allPoints) {
        String digit = line.id().replaceAll("\\D", "");
        List<String> candidates = allPoints.stream()
                .filter(point -> point.contains(digit))
                .limit(4)
                .toList();
        if (!candidates.isEmpty()) {
            return candidates;
        }
        return allPoints.stream().limit(3).toList();
    }

    private List<String> inferredAxleCounters(YardLine line, Set<String> allAxles) {
        String digit = line.id().replaceAll("\\D", "");
        List<String> candidates = allAxles.stream()
                .filter(axle -> axle.contains(digit))
                .limit(3)
                .toList();
        if (!candidates.isEmpty()) {
            return candidates;
        }
        return allAxles.stream().limit(2).toList();
    }

    private List<RccRoute> attachConflicts(List<RccRoute> routes) {
        List<RccRoute> result = new ArrayList<>();
        for (RccRoute route : routes) {
            List<String> conflicts = routes.stream()
                    .filter(other -> !other.routeId().equals(route.routeId()))
                    .filter(other -> conflicts(route, other))
                    .map(RccRoute::routeId)
                    .toList();
            result.add(new RccRoute(
                    route.routeId(),
                    route.routeName(),
                    route.fromSignal(),
                    route.toSignalOrLine(),
                    route.direction(),
                    route.routeType(),
                    route.trackCircuitsOrAxleCounters(),
                    route.pointsNormal(),
                    route.pointsReverse(),
                    route.overlapOrIsolation(),
                    conflicts,
                    route.sourceEvidence(),
                    route.reviewStatus()
            ));
        }
        return result;
    }

    private static boolean conflicts(RccRoute left, RccRoute right) {
        return left.fromSignal().equals(right.fromSignal())
                || intersects(left.pointsReverse(), right.pointsReverse())
                || intersects(left.trackCircuitsOrAxleCounters(), right.trackCircuitsOrAxleCounters());
    }

    private static boolean intersects(List<String> left, List<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.retainAll(right);
        return !values.isEmpty();
    }

    private static List<String> unique(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String formatMeters(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
