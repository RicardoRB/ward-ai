package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConsecutiveAppendsCheck;
import dev.ward.analysis.domain.rule.check.StringBufferCapacityCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of the rules about StringBuilder and StringBuffer appends. */
final class StringBuilderChecks {

  private static final String MESSAGE = "message";

  private StringBuilderChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("consecutive-appends-should-reuse", StringBuilderChecks::consecutiveAppends),
        entry("insufficient-string-buffer-declaration", StringBuilderChecks::capacity));
  }

  private static ConsecutiveAppendsCheck consecutiveAppends(final TomlParseResult toml) {
    return new ConsecutiveAppendsCheck(TomlValues.required(toml, MESSAGE));
  }

  private static StringBufferCapacityCheck capacity(final TomlParseResult toml) {
    return new StringBufferCapacityCheck(TomlValues.required(toml, MESSAGE));
  }
}
