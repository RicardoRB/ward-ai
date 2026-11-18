package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The report and the labels of the Python corpus run: the places of the findings, the places that
 * need a label, and the text that is compared with the committed report.
 */
final class PythonCorpusReport {
  /** How many findings of a rule that is not a security rule need a label. */
  static final int SAMPLE = 5;

  private static final String SECURITY = "security";
  private static final int FIELDS = 4;
  private static final String SEPARATOR = " \\| ";
  private static final String HEADER =
      "# %s %s (%s) against Ward's Python rules.\n"
          + "# Archive sha-256 %s.\n"
          + "# Every ward:python/* rule of the standard preset and every Python rule of category"
          + " security ran over every .py file of the archive.\n"
          + "# A labelled finding is true (tp), false (fp) or undetermined (un), as read from the"
          + " code at its line; the labels are in %s. Every finding of a"
          + " security rule is labelled; of another rule, its first %d findings in path order.\n"
          + "# The measured cost of the run is in %s.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Djacoco.skip=true -Dtest=%s"
          + " -D%s=<dir> -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  /** A finding in a place of the source. */
  record Place(String rule, String location) {}

  private PythonCorpusReport() {}

  /** Tells whether {@code rule} is a security rule, of which every finding needs a label. */
  static boolean isSecurity(final RuleDefinition rule) {
    return SECURITY.equals(rule.category());
  }

  /** Returns the distinct places of each rule, in path order and then line order. */
  static Map<String, List<Place>> places(final List<AnalysisFinding> findings) {
    final Map<String, List<Place>> places = new LinkedHashMap<>();
    findings.stream()
        .sorted(
            (left, right) -> {
              final int byFile = left.file().compareTo(right.file());
              return byFile == 0 ? Integer.compare(line(left), line(right)) : byFile;
            }
        )
        .map(finding -> place(finding.ruleId(), finding.file() + ":" + line(finding)))
        .distinct()
        .forEach(
            place -> places.computeIfAbsent(place.rule(), rule -> new ArrayList<>()).add(place)
        );
    return places;
  }

  private static int line(final AnalysisFinding finding) {
    return finding.line() == null ? 0 : finding.line();
  }

  /** Returns the places that need a label: all of a security rule, a sample of another rule. */
  static List<Place> needed(
      final List<RuleDefinition> rules,
      final Map<String,
      List<Place>> found
  ) {
    final List<Place> needed = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      final List<Place> places = found.getOrDefault(rule.id(), List.of());
      needed.addAll(isSecurity(rule) ? places : places.subList(0, Math.min(SAMPLE, places.size())));
    }
    return needed;
  }

  static String report(
      final PythonCorpusSpec spec,
      final List<RuleDefinition> rules,
      final int files,
      final Map<String,
      List<Place>> found,
      final Map<Place,
      String> labels
  ) {
    final StringBuilder report = new StringBuilder(
        HEADER.formatted(
            spec.title(),
            spec.tag(),
            spec.description(),
            spec.sha256(),
            spec.labels(),
            SAMPLE,
            spec.cost(),
            spec.testClass(),
            spec.property()
        )
    );
    final int total = found.values().stream().mapToInt(List::size).sum();
    report.append("files %d | findings %d%n".formatted(files, total));
    for (final RuleDefinition rule : rules) {
      report.append(line(rule.id(), found.getOrDefault(rule.id(), List.of()), labels));
    }
    return report.toString();
  }

  private static String line(
      final String rule,
      final List<Place> places,
      final Map<Place,
      String> labels
  ) {
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final String label : List.of("tp", "fp", "un")) {
      counts.put(label, 0);
    }
    places.stream()
        .filter(labels::containsKey)
        .forEach(place -> counts.merge(labels.get(place), 1, Integer::sum));
    return "%s | findings %d | labelled %d | tp %d | fp %d | un %d%n"
        .formatted(
            rule,
            places.size(),
            counts.values().stream().mapToInt(Integer::intValue).sum(),
            counts.get("tp"),
            counts.get("fp"),
            counts.get("un")
        );
  }

  /** Returns the message of the first finding at each place, on one line. */
  static Map<Place, String> messages(final List<AnalysisFinding> findings) {
    final Map<Place, String> messages = new LinkedHashMap<>();
    findings.forEach(
        finding ->
            messages.putIfAbsent(
                place(finding.ruleId(), finding.file() + ":" + line(finding)),
                finding.message().replaceAll("\\s+", " ")
            )
    );
    return messages;
  }

  /** Lists the places that have no label, each with the message of its finding. */
  static String listed(
      final List<Place> places,
      final Map<Place,
      String> labels,
      final Map<Place,
      String> messages
  ) {
    final StringBuilder text = new StringBuilder();
    places.stream()
        .filter(place -> !labels.containsKey(place))
        .forEach(
            place ->
                text.append(
                    "%s | %s | ? | %s%n"
                        .formatted(place.rule(), place.location(), messages.get(place))
                )
        );
    return text.toString();
  }

  static Map<Place, String> labels(final PythonCorpusSpec spec) throws IOException {
    final Map<Place, String> labels = new LinkedHashMap<>();
    Files.readAllLines(
        Path.of("src/test/resources").resolve("benchmark/" + spec.labels()),
        StandardCharsets.UTF_8
    )
        .stream()
        .filter(text -> !text.startsWith("#") && !text.isBlank())
        .map(text -> text.split(SEPARATOR, FIELDS))
        .forEach(parts -> labels.put(place(parts[0].trim(), parts[1].trim()), parts[2].trim()));
    return labels;
  }

  private static Place place(final String rule, final String location) {
    return new Place(rule, location);
  }
}
