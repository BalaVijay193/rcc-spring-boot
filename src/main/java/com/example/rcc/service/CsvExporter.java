package com.example.rcc.service;

import com.example.rcc.model.RccRoute;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CsvExporter {
    public String toCsv(List<RccRoute> routes) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of(
                "Route Id",
                "Route Name",
                "From Signal",
                "To Signal/Line",
                "Direction",
                "Route Type",
                "Track Circuits/Axle Counters",
                "Points Normal",
                "Points Reverse",
                "Overlap/Isolation",
                "Conflicting Routes",
                "Source Evidence",
                "Review Status"
        ));
        for (RccRoute route : routes) {
            appendRow(csv, List.of(
                    route.routeId(),
                    route.routeName(),
                    route.fromSignal(),
                    route.toSignalOrLine(),
                    route.direction(),
                    route.routeType(),
                    String.join("; ", route.trackCircuitsOrAxleCounters()),
                    String.join("; ", route.pointsNormal()),
                    String.join("; ", route.pointsReverse()),
                    String.join("; ", route.overlapOrIsolation()),
                    String.join("; ", route.conflictingRoutes()),
                    route.sourceEvidence(),
                    route.reviewStatus()
            ));
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(columns.get(i)));
        }
        csv.append('\n');
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
