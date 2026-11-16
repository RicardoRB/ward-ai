package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The labels of the findings of a measured corpus: each line of a labels file says whether the
 * finding of a rule at a place is true ({@code tp}), false ({@code fp}) or undetermined ({@code
 * un}), as read from the code at that line.
 */
final class CorpusLabels {
  private static final int FIELDS = 4;
  private static final String SEPARATOR = " \\| ";
  private static final List<String> KINDS = List.of("tp", "fp", "un");

  private CorpusLabels() {}

  /** A finding in a place of the source. */
  record Place(String rule, String location) {}

  /** Reads {@code rule | file:line | label | reason} lines; a missing file has no labels. */
  static Map<Place, String> read(final Path file) throws IOException {
    final Map<Place, String> labels = new LinkedHashMap<>();
    if (!Files.exists(file)) {
      return labels;
    }
    Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(text -> !text.startsWith("#") && !text.isBlank())
        .map(text -> text.split(SEPARATOR, FIELDS))
        .forEach(parts -> labels.put(new Place(parts[0].trim(), parts[1].trim()), parts[2].trim()));
    return labels;
  }

  /** Returns the distinct places of the findings, by rule and then by location. */
  static List<Place> places(final List<AnalysisFinding> findings) {
    return findings.stream()
        .map(finding -> new Place(finding.ruleId(), finding.file() + ":" + finding.line()))
        .distinct()
        .sorted(Comparator.comparing(Place::rule).thenComparing(Place::location))
        .toList();
  }

  /** Returns {@code tp N | fp N | un N} for the given places, which all have a label. */
  static String counts(final List<Place> places, final Map<Place, String> labels) {
    final Map<String, Integer> counts = new LinkedHashMap<>();
    KINDS.forEach(kind -> counts.put(kind, 0));
    places.forEach(place -> counts.merge(labels.get(place), 1, Integer::sum));
    return "tp %d | fp %d | un %d"
        .formatted(counts.get("tp"), counts.get("fp"), counts.get("un"));
  }

  /** Returns the paths of the units that were left without analysis, in path order. */
  static List<String> notAnalysed(final RuleRunResult result) {
    return result.executionErrors().stream()
        .filter(error -> error.ruleId() == null && error.path() != null)
        .map(RuleExecutionError::path)
        .distinct()
        .sorted()
        .toList();
  }
}
