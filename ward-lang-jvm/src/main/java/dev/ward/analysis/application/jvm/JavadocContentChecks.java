package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.InvalidJavadocPositionCheck;
import dev.ward.analysis.domain.rule.check.JavadocMethodTagsCheck;
import dev.ward.analysis.domain.rule.check.JavadocParagraphCheck;
import dev.ward.analysis.domain.rule.check.JavadocSummaryCheck;
import java.util.Map;

/**
 * Builds the checks of the Javadoc content rules: where a comment sits, what its summary and
 * paragraphs look like, and whether a method's tags match its signature.
 */
final class JavadocContentChecks {

  private static final String MESSAGE = "message";

  private JavadocContentChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "invalid-javadoc-position",
            toml -> new InvalidJavadocPositionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "summary-javadoc", toml -> new JavadocSummaryCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "javadoc-paragraph",
            toml -> new JavadocParagraphCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "javadoc-method",
            toml -> new JavadocMethodTagsCheck(TomlValues.required(toml, MESSAGE))));
  }
}
