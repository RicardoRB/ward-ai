package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinNewLineAtEndOfFileCheck;
import dev.ward.analysis.domain.rule.check.KotlinTrailingWhitespaceCheck;
import java.util.Map;

/** Builds the Kotlin checks of detekt's style set that read the file text, not the tree. */
final class KotlinTextChecks {

  private static final String MESSAGE = "message";

  private KotlinTextChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-new-line-at-end-of-file",
            toml -> new KotlinNewLineAtEndOfFileCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-trailing-whitespace",
            toml -> new KotlinTrailingWhitespaceCheck(TomlValues.required(toml, MESSAGE))));
  }
}
