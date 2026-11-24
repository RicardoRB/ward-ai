package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Which findings of a corpus run need a label, and which unparsable files are known (#760). */
final class CorpusSampling {
  private static final String SECURITY = "security";
  private static final Set<String> DIFFERENTIAL =
      Set.of("ruoyi", "mall", "eladmin", "ktor-samples", "fastapi-template", "nest", "vapor");

  private CorpusSampling() {}

  /**
   * The findings that need a label. Every finding of a security rule does; a finding of another
   * rule in a project of the differential list does not, because the differential report shows a
   * human what a rule change adds there.
   */
  static List<Place> sampled(final List<RuleDefinition> rules, final List<Place> found) {
    final Set<String> security =
        rules.stream()
            .filter(rule -> SECURITY.equals(rule.category()))
            .map(RuleDefinition::id)
            .collect(Collectors.toSet());
    return found.stream()
        .filter(
            place ->
                security.contains(place.rule())
                    || DIFFERENTIAL.stream()
                        .noneMatch(project -> place.location().startsWith(project + "/"))
        )
        .toList();
  }

  /**
   * Whether a not-analysed line names a file of {@code benchmark/<key>-corpus-parser-gaps.txt}: a
   * valid file that the parser rejects, listed with an open issue.
   */
  static boolean parserGap(final Path resources, final String key, final String line) {
    final Path gaps = resources.resolve(key + "-corpus-parser-gaps.txt");
    try {
      return Files.exists(gaps)
          && Files.readAllLines(gaps).stream()
              .filter(text -> !text.startsWith("#") && !text.isBlank())
              .map(text -> text.split(" \\| ", 2) [0].trim() + ":")
              .anyMatch(line::startsWith);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
