package dev.ward.infrastructure.rule;

import java.util.Set;

/**
 * The bundled rules that belong to no preset, so a project selects them with {@code include}. Each
 * contradicts another rule or the formatter, and neither Checkstyle nor PMD enables it by default.
 */
public final class OptInOnlyRules {

  /** The ids, with the reason each is out of the presets in the comment above it. */
  public static final Set<String> IDS =
      Set.of(
          // the formatter puts every annotation on its own line
          "ward:java/annotation-on-same-line",
          // declaring a variable near its use is the usual style, and PMD's quickstart omits it
          "ward:java/local-variable-declaration-should-be-at-start-of-block");

  private OptInOnlyRules() {}
}
