package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.EmptyCatchBlockCheck;
import dev.ward.analysis.domain.rule.check.GoogleRightCurlyCheck;
import dev.ward.analysis.domain.rule.check.LeftCurlyCheck;
import dev.ward.analysis.domain.rule.check.NeedBracesCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java block checks that a rule body names. Each parameter is a rule option, named after
 * the Checkstyle property in kebab-case.
 */
final class BlockChecks {

  private static final String MESSAGE = "message";
  private static final String COMMENT_FORMAT = "comment-format";
  private static final String EXCEPTION_VARIABLE_NAME = "exception-variable-name";
  private static final String IGNORE_ENUMS = "ignore-enums";
  private static final String ALLOW_EMPTY_LOOP_BODY = "allow-empty-loop-body";

  private static final Set<String> CHECKS =
      Set.of("need-braces", "left-curly", "google-right-curly", "empty-catch-block");

  private BlockChecks() {}

  /** Returns whether {@code name} is one of the block checks. */
  static boolean isBlockCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "need-braces" ->
          new NeedBracesCheck(flag(toml, ALLOW_EMPTY_LOOP_BODY, false), message(toml));
      case "left-curly" -> new LeftCurlyCheck(flag(toml, IGNORE_ENUMS, true), message(toml));
      case "google-right-curly" -> new GoogleRightCurlyCheck(message(toml));
      default -> emptyCatchBlock(toml);
    };
  }

  private static RuleCheck emptyCatchBlock(final TomlParseResult toml) {
    return new EmptyCatchBlockCheck(
        Pattern.compile(Optional.ofNullable(toml.getString(COMMENT_FORMAT)).orElse(".*")),
        Pattern.compile(Optional.ofNullable(toml.getString(EXCEPTION_VARIABLE_NAME)).orElse("^$")),
        message(toml));
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
