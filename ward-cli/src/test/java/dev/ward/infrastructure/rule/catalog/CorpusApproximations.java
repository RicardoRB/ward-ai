package dev.ward.infrastructure.rule.catalog;

import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The findings of a corpus run that were labelled {@code fp} and are listed in {@code
 * benchmark/<language>-corpus-approximations.txt} as {@code rule | file:line | reason | #issue}
 * (#698). A line without an issue number does not count as listed.
 */
final class CorpusApproximations {
  private static final int FIELDS = 4;
  private static final int ISSUE_FIELD = 3;

  private CorpusApproximations() {}

  /** Returns the places labelled {@code fp} that the approximations file does not list. */
  static List<Place> unlisted(
      final Path resources,
      final String key,
      final List<Place> found,
      final Map<Place,
      String> labels
  ) throws IOException {
    final Path file = resources.resolve(key + "-corpus-approximations.txt");
    final Set<Place> listed = new HashSet<>();
    if (Files.exists(file)) {
      for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        final String[] parts = line.split(" \\| ", FIELDS);
        if (!line.startsWith("#") && parts.length == FIELDS && parts[ISSUE_FIELD].matches(
            ".*#\\d+.*"
        )) {
          listed.add(new Place(parts[0].trim(), parts[1].trim()));
        }
      }
    }
    return found.stream()
        .filter(place -> "fp".equals(labels.get(place)))
        .filter(place -> !listed.contains(place))
        .toList();
  }
}
