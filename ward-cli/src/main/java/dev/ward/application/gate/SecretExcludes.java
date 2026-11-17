package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.ExcludeGlob;
import dev.ward.domain.config.SecretsOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Compiles the {@code exclude-paths} globs of a {@code secrets} check. */
final class SecretExcludes {
  private SecretExcludes() {}

  /** Returns the patterns of the globs; an invalid glob adds a problem naming the check. */
  static List<Pattern> compile(
      final String name,
      final SecretsOptions options,
      final List<String> problems
  ) {
    final List<Pattern> patterns = new ArrayList<>();
    for (final String glob : options.excludePaths()) {
      try {
        patterns.add(ExcludeGlob.compile(glob));
      } catch (final IllegalArgumentException exception) {
        problems.add(
            "ward.toml: [checks." + name + "] exclude-paths: invalid glob \"" + glob + "\""
        );
      }
    }
    return patterns;
  }
}
