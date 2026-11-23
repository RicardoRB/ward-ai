package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.ForbiddenNodeCheck;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** Reads the conditions of a {@code forbidden-node} rule on where the node stands. */
final class GenericPosition {
  private GenericPosition() {}

  static ForbiddenNodeCheck.Position of(final TomlParseResult toml) {
    return new ForbiddenNodeCheck.Position(
        Optional.ofNullable(toml.getString("spaced-text")).map(Pattern::compile),
        toml.getString("last-in", () -> ""),
        Optional.ofNullable(toml.getString("not-previous-text")).map(Pattern::compile),
        toml.getString("previous-of", () -> "")
    );
  }
}
