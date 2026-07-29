package com.example.rcc.service;

import com.example.rcc.model.ExtractedPdf;
import com.example.rcc.model.RccRoute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RccTableParser {
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+[-.]?\\s+)?(?<signal>(?:SH|S|CO)\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?)\\s+"
                    + "(?<destination>.{1,42}?)\\s+"
                    + "(?<button>(?:SH|S|CO)\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?)\\s+"
                    + "(?<route>UP|DN|DM|UM|HG|MAIN|LOOP|[A-Z]{1,4}\\s*-?\\s*\\d+[A-Z]?)\\b"
                    + "(?<rest>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_ROW_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+[-.]?\\s+)?(?<signal>CO\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?)\\s+"
                    + "(?<destination>(?:SH|S)\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?)\\s+"
                    + "(?<route>CH\\s*\\d+|CGGN|COGGN|HG)\\b(?<rest>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACK_PATTERN = Pattern.compile("\\b\\d+[A-Z]?\\s*(?:AXT|BXT|XT|T)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POINT_PATTERN = Pattern.compile("\\b(?:P\\s*-?\\s*)?\\d{2,3}\\s*(?:A\\s*/\\s*B|N\\s*B|R\\s*B|NB|RB|A/B|A|B)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_REF_PATTERN = Pattern.compile("\\b(?:SH|S|CO)\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?\\s*-\\s*[A-Z]{1,4}\\s*-?\\s*\\d+[A-Z]?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNAL_PATTERN = Pattern.compile("\\b(?:SH|S|CO)\\s*-?\\s*\\d+(?:\\(\\d+\\))?[A-Z]?\\b", Pattern.CASE_INSENSITIVE);

    public List<RccRoute> parseRoutes(ExtractedPdf referencePdf) {
        List<RccRoute> routes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int sequence = 1;
        for (String rawLine : referencePdf.text().split("\\R")) {
            String line = TextNormalizer.cleanLabel(rawLine);
            if (line.isBlank() || isHeaderOrFooter(line)) {
                continue;
            }
            boolean preferControlRow = line.toUpperCase(Locale.ROOT).matches("^\\s*CO\\s*-?\\s*\\d+.*");
            Matcher matcher = preferControlRow ? CONTROL_ROW_PATTERN.matcher(line) : ROW_PATTERN.matcher(line);
            boolean matched = matcher.find();
            if (!matched && !preferControlRow) {
                matcher = CONTROL_ROW_PATTERN.matcher(line);
                matched = matcher.find();
            }
            if (!matched) {
                continue;
            }
            String fromSignal = canonicalSignal(matcher.group("signal"));
            String to = TextNormalizer.cleanLabel(matcher.group("destination"));
            String routeButton = canonicalRouteButton(matcher.group("route"));
            String rest = matcher.group("rest");
            if (!hasRouteEvidence(rest)) {
                continue;
            }
            String routeKey = fromSignal + "|" + to + "|" + routeButton;
            if (!seen.add(routeKey)) {
                continue;
            }
            List<String> tracks = findTrackCircuits(line);
            List<String> points = findPoints(rest);
            List<String> conflicts = findRouteRefs(rest);
            routes.add(new RccRoute(
                    "R" + String.format(Locale.ROOT, "%03d", sequence++),
                    fromSignal + " -> " + to + " [" + routeButton + "]",
                    fromSignal,
                    to + " / " + routeButton,
                    inferDirection(to + " " + routeButton),
                    "REFERENCE_RCC_ROW",
                    tracks,
                    points,
                    List.of(),
                    findOverlap(rest),
                    conflicts,
                    line,
                    "REFERENCE_RCC_EXTRACTED"
            ));
        }
        return routes;
    }

    public Set<String> parseSignals(ExtractedPdf referencePdf) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = SIGNAL_PATTERN.matcher(referencePdf.text());
        while (matcher.find()) {
            values.add(canonicalSignal(matcher.group()));
        }
        return values;
    }

    public Set<String> parsePoints(ExtractedPdf referencePdf) {
        return new LinkedHashSet<>(findPoints(referencePdf.text()));
    }

    public Set<String> parseTrackCircuits(ExtractedPdf referencePdf) {
        return new LinkedHashSet<>(findTrackCircuits(referencePdf.text()));
    }

    private static boolean isHeaderOrFooter(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("SIGNAL NO")
                || upper.contains("ROUTE CONTROL CHART")
                || upper.contains("SELECTION TABLE")
                || upper.contains("TRACK CIRCUITS")
                || upper.contains("CENTRAL RAILWAY")
                || upper.contains("HARYANA RAIL")
                || upper.contains("NAME / DESIGNATION");
    }

    private static boolean hasRouteEvidence(String rest) {
        return TRACK_PATTERN.matcher(rest).find()
                || POINT_PATTERN.matcher(rest).find()
                || ROUTE_REF_PATTERN.matcher(rest).find();
    }

    private static List<String> findTrackCircuits(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = TRACK_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(TextNormalizer.compactObjectName(matcher.group()));
        }
        return new ArrayList<>(values);
    }

    private static List<String> findPoints(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = POINT_PATTERN.matcher(text);
        while (matcher.find()) {
            String value = TextNormalizer.cleanLabel(matcher.group()).toUpperCase(Locale.ROOT);
            if (value.endsWith("T") || value.endsWith("XT") || value.length() < 2) {
                continue;
            }
            if (value.matches("\\d{3,4}") && Integer.parseInt(value) >= 120) {
                continue;
            }
            values.add(value.replaceAll("\\s+", " "));
        }
        return new ArrayList<>(values);
    }

    private static List<String> findRouteRefs(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = ROUTE_REF_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(canonicalRouteReference(matcher.group()));
        }
        return new ArrayList<>(values);
    }

    private static List<String> findOverlap(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        if (!upper.contains("OVERLAP") && !upper.contains("OV")) {
            return List.of();
        }
        return findTrackCircuits(text).stream().limit(4).toList();
    }

    private static String canonicalSignal(String signal) {
        return SipParser.canonicalize(signal);
    }

    private static String canonicalRouteButton(String routeButton) {
        return TextNormalizer.compactObjectName(routeButton).replaceAll("([A-Z]+)-?(\\d)", "$1-$2");
    }

    private static String canonicalRouteReference(String routeRef) {
        return TextNormalizer.compactObjectName(routeRef).replaceAll("([A-Z]+\\d+[A-Z]?(?:\\(\\d+\\))?)-([A-Z]+)-?(\\d)", "$1-$2$3");
    }

    private static String inferDirection(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains(" DN") || upper.startsWith("DN") || upper.contains("DL")) {
            return "DOWN";
        }
        if (upper.contains(" UP") || upper.startsWith("UP") || upper.contains("UL")) {
            return "UP";
        }
        return "UNKNOWN";
    }
}
