package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BlockCommentAsteriskCheck;
import dev.ward.analysis.domain.rule.check.JavadocAsteriskCheck;
import dev.ward.analysis.domain.rule.check.JavadocLayoutCheck;
import dev.ward.analysis.domain.rule.check.OuterTypeFilenameCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks of the Javadoc layout rules: the leading asterisks of a comment, and where its
 * text and its block tags sit.
 */
final class JavadocLayoutChecks {

  private static final String MESSAGE = "message";

  private JavadocLayoutChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "javadoc-leading-asterisk-align",
            toml -> asterisk(JavadocAsteriskCheck.Mode.ALIGN, toml)),
        entry(
            "javadoc-missing-leading-asterisk",
            toml -> asterisk(JavadocAsteriskCheck.Mode.MISSING, toml)),
        entry(
            "javadoc-missing-whitespace-after-asterisk",
            toml -> asterisk(JavadocAsteriskCheck.Mode.WHITESPACE, toml)),
        entry(
            "multiline-comment-leading-asterisk-presence",
            toml -> new BlockCommentAsteriskCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "outer-type-filename",
            toml -> new OuterTypeFilenameCheck(TomlValues.required(toml, MESSAGE))),
        entry("single-line-javadoc", toml -> layout(JavadocLayoutCheck.Mode.SINGLE_LINE, toml)),
        entry(
            "javadoc-content-location",
            toml -> layout(JavadocLayoutCheck.Mode.CONTENT_LOCATION, toml)),
        entry(
            "require-empty-line-before-block-tag-group",
            toml -> layout(JavadocLayoutCheck.Mode.EMPTY_LINE_BEFORE_TAGS, toml)));
  }

  private static JavadocAsteriskCheck asterisk(
      final JavadocAsteriskCheck.Mode mode, final TomlParseResult toml) {
    return new JavadocAsteriskCheck(mode, TomlValues.required(toml, MESSAGE));
  }

  private static JavadocLayoutCheck layout(
      final JavadocLayoutCheck.Mode mode, final TomlParseResult toml) {
    return new JavadocLayoutCheck(mode, TomlValues.required(toml, MESSAGE));
  }
}
