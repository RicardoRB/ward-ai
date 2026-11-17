package dev.ward.infrastructure.rule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * The bundled rules that belong to no preset, so a project selects them with {@code include}: the
 * rules that contradict another rule or the formatter, and neither Checkstyle nor PMD enables
 * them by default, and the rules left out because another rule of the presets reports the same
 * place ({@code presets/duplicates-dropped.txt}).
 */
public final class OptInOnlyRules {
  /** The ids, with the reason each is out of the presets in the comment above it. */
  public static final Set<String> IDS = ids();

  private static final String DROPPED = "/presets/duplicates-dropped.txt";

  private OptInOnlyRules() {}

  private static Set<String> ids() {
    final Set<String> ids = new HashSet<>();
    // the formatter puts every annotation on its own line
    ids.add("ward:java/annotation-on-same-line");
    // declaring a variable near its use is the usual style, and PMD's quickstart omits it
    ids.add("ward:java/local-variable-declaration-should-be-at-start-of-block");
    try (InputStream stream = OptInOnlyRules.class.getResourceAsStream(DROPPED)) {
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> line.startsWith("ward:"))
          .map(line -> line.substring(0, line.indexOf(" | ")))
          .forEach(ids::add);
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return Set.copyOf(ids);
  }
}
