package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.LockfileFormatException;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Parses a lockfile as TOML. */
final class TomlSupport {
  private TomlSupport() {}

  /**
   * Parses {@code text}.
   *
   * @throws LockfileFormatException if it is not valid TOML
   */
  static TomlParseResult parse(final String text) {
    final TomlParseResult parsed = Toml.parse(text);
    if (parsed.hasErrors()) {
      throw new LockfileFormatException("it is not valid TOML");
    }
    return parsed;
  }
}
