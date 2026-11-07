package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AvoidNestedBlocksCheck;
import dev.ward.analysis.domain.rule.check.EmptyBlockCheck;
import dev.ward.analysis.domain.rule.check.EmptyCatchBlockCheck;
import dev.ward.analysis.domain.rule.check.GoogleRightCurlyCheck;
import dev.ward.analysis.domain.rule.check.LeftCurlyCheck;
import dev.ward.analysis.domain.rule.check.NeedBracesCheck;
import dev.ward.analysis.domain.rule.check.RightCurlyCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.HashMap;
import java.util.Map;
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

  private static final String ALLOW_IN_SWITCH_CASE = "allow-in-switch-case";
  private static final String OPTION = "option";

  private static final Set<String> CHECKS =
      Set.of(
          "need-braces",
          "left-curly",
          "google-right-curly",
          "empty-catch-block",
          "avoid-nested-blocks",
          "right-curly",
          "empty-block");

  private BlockChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new HashMap<>();
    for (final String name : CHECKS) {
      checks.put(name, toml -> build(name, toml));
    }
    return Map.copyOf(checks);
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
      case "avoid-nested-blocks" ->
          new AvoidNestedBlocksCheck(flag(toml, ALLOW_IN_SWITCH_CASE, false), message(toml));
      case "right-curly" -> new RightCurlyCheck(TomlValues.required(toml, OPTION), message(toml));
      case "empty-block" -> new EmptyBlockCheck(TomlValues.required(toml, OPTION), message(toml));
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
