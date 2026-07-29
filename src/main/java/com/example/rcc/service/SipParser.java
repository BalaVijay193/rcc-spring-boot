package com.example.rcc.service;

import com.example.rcc.model.ExtractedPdf;
import com.example.rcc.model.YardLine;
import com.example.rcc.model.YardModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SipParser {
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "\\bL\\s*-\\s*(\\d+)\\b(?<desc>.*?)(?=(?:\\bL\\s*-\\s*\\d+\\b)|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSR_PATTERN = Pattern.compile("\\bCSR\\s*([0-9]+(?:\\.[0-9]+)?)\\s*m", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNAL_PATTERN = Pattern.compile("\\b(?:SH|DS|DE|DP|S)\\s*-?\\s*\\d+[A-Z]?(?:\\s*/\\s*(?:DS|SH)?\\s*\\d+[A-Z]?)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_SIGNAL_PATTERN = Pattern.compile("\\bSH\\s*-?\\s*\\d+[A-Z]?(?:\\s*/\\s*(?:DS|SH)?\\s*\\d+[A-Z]?)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POINT_PATTERN = Pattern.compile("\\b(?:P\\s*-\\s*\\d+[A-Z]?(?:\\s*/\\s*[A-Z])?|P\\d+[A-Z]?(?:\\s*/\\s*[A-Z])?|PT(?:\\.\\s*NO)?\\s*-?\\s*\\d+[A-Z]?|PT\\s*NO\\s*-?\\s*\\d+[A-Z]?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AXLE_PATTERN = Pattern.compile("\\b\\d+[A-Z]?\\s*X\\s*T\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEAD_END_PATTERN = Pattern.compile("\\bDE\\s*-?\\s*\\d+[A-Z]?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLATFORM_PATTERN = Pattern.compile("\\b(?:RAIL\\s+LEVEL\\s+)?PLATFORM\\b", Pattern.CASE_INSENSITIVE);

    public YardModel parse(ExtractedPdf extractedPdf) {
        String text = TextNormalizer.cleanLabel(extractedPdf.text());
        Set<String> signals = findAll(SIGNAL_PATTERN, text);
        Set<String> points = findAll(POINT_PATTERN, text);
        Set<String> axleCounters = findAll(AXLE_PATTERN, text);
        Set<String> deadEnds = findAll(DEAD_END_PATTERN, text);
        Set<String> platforms = findPlatformMentions(text);

        List<YardLine> lines = parseYardLines(extractedPdf.text());
        List<String> notes = new ArrayList<>();
        notes.add("Routes are generated from PDF text labels and must be checked against the SIP drawing geometry.");
        if (lines.isEmpty()) {
            notes.add("No L-n yard line descriptions were found; upload a SIP with extractable text labels.");
        }
        if (signals.isEmpty()) {
            notes.add("No signal labels were found.");
        }

        return new YardModel(lines, signals, points, axleCounters, deadEnds, platforms, notes);
    }

    private List<YardLine> parseYardLines(String rawText) {
        List<YardLine> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String row : rawText.split("\\R")) {
            String cleanedRow = TextNormalizer.cleanLabel(row);
            if (cleanedRow.isBlank()) {
                continue;
            }
            Matcher matcher = LINE_PATTERN.matcher(cleanedRow);
            while (matcher.find()) {
                String id = "L-" + matcher.group(1);
                if (!seen.add(id)) {
                    continue;
                }
                int contextStart = Math.max(0, matcher.start() - 120);
                String fragment = cleanedRow.substring(contextStart);
                if (fragment.length() > 360) {
                    fragment = fragment.substring(0, 360);
                }
                String routeFragment = routeBoundaryFragment(fragment);
                double csr = parseCsr(fragment);
                lines.add(new YardLine(
                        id,
                        bestDescription(id, fragment),
                        csr,
                        List.copyOf(findAll(ROUTE_SIGNAL_PATTERN, routeFragment)),
                        List.copyOf(findAll(POINT_PATTERN, fragment)),
                        List.copyOf(findAll(AXLE_PATTERN, fragment)),
                        List.copyOf(findAll(DEAD_END_PATTERN, fragment))
                ));
            }
        }
        return lines;
    }

    private static String routeBoundaryFragment(String fragment) {
        Matcher csr = CSR_PATTERN.matcher(fragment);
        if (csr.find()) {
            return fragment.substring(0, csr.end());
        }
        return fragment;
    }

    private static String bestDescription(String id, String fragment) {
        String compact = fragment.replaceAll("\\s+", " ").trim();
        int csrAt = compact.toUpperCase(Locale.ROOT).indexOf("CSR");
        int end = csrAt > 0 ? Math.min(compact.length(), csrAt + 24) : Math.min(compact.length(), 160);
        return (id + " " + compact.substring(0, end)).trim();
    }

    private static double parseCsr(String fragment) {
        Matcher matcher = CSR_PATTERN.matcher(fragment);
        if (!matcher.find()) {
            return 0;
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static Set<String> findAll(Pattern pattern, String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = canonicalize(matcher.group());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static Set<String> findPlatformMentions(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = PLATFORM_PATTERN.matcher(text);
        int index = 1;
        while (matcher.find()) {
            values.add("PLATFORM-" + index++);
        }
        return values;
    }

    static String canonicalize(String value) {
        String compact = TextNormalizer.compactObjectName(value);
        compact = compact.replaceAll("^(SH|DS|DE|DP|CO|S|P|PT)-?(\\d)", "$1-$2");
        compact = compact.replaceAll("^PT\\.NO-?", "P-");
        compact = compact.replaceAll("^PTNO-?", "P-");
        compact = compact.replaceAll("^PT-?", "P-");
        compact = compact.replaceAll("([0-9A-Z])X\\s*T$", "$1XT");
        compact = compact.replaceAll("/(DS|SH)?(\\d)", "/$1$2");
        return compact.replace("/DS", "/DS");
    }
}
