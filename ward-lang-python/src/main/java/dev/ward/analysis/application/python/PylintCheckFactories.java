package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ClassOverrideCheck;
import dev.ward.analysis.domain.rule.check.ClassProtocolCheck;
import dev.ward.analysis.domain.rule.check.DunderReturnCheck;
import dev.ward.analysis.domain.rule.check.ElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.ExceptionStatementCheck;
import dev.ward.analysis.domain.rule.check.FieldCallCheck;
import dev.ward.analysis.domain.rule.check.FirstArgumentCheck;
import dev.ward.analysis.domain.rule.check.FormatIndexCheck;
import dev.ward.analysis.domain.rule.check.KeywordArgBeforeVarargCheck;
import dev.ward.analysis.domain.rule.check.LoggingFormatCheck;
import dev.ward.analysis.domain.rule.check.MatchPositionalCheck;
import dev.ward.analysis.domain.rule.check.ModifiedIterationCheck;
import dev.ward.analysis.domain.rule.check.NamedExprWithoutContextCheck;
import dev.ward.analysis.domain.rule.check.NonlocalCheck;
import dev.ward.analysis.domain.rule.check.PointlessStringCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SameModuleCallCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The Pylint-derived Python checks that the rule TOMLs name, registered by their check names. */
final class PylintCheckFactories {

  private static final String MESSAGE = "message";

  private PylintCheckFactories() {}

  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("dunder-return", PylintCheckFactories::dunderReturn),
        entry(
            "else-after-jump",
            toml ->
                new ElseAfterJumpCheck(
                    TomlValues.required(toml, "jump"), TomlValues.required(toml, MESSAGE))),
        entry("pointless-string", toml -> new PointlessStringCheck()),
        entry("logging-format", PylintCheckFactories::loggingFormat),
        entry(
            "exception-statement",
            toml -> new ExceptionStatementCheck(TomlValues.required(toml, MESSAGE))),
        entry("field-call", toml -> new FieldCallCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "match-positional",
            toml -> new MatchPositionalCheck(TomlValues.required(toml, MESSAGE))),
        entry("same-module-call", PylintCheckFactories::sameModuleCall),
        entry("class-protocol", PylintCheckFactories::classProtocol),
        entry("class-override", PylintCheckFactories::classOverride),
        entry("modified-iteration", PylintCheckFactories::modifiedIteration),
        entry("nonlocal", PylintCheckFactories::nonlocal),
        entry("format-index", toml -> new FormatIndexCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "keyword-arg-before-vararg",
            toml -> new KeywordArgBeforeVarargCheck(TomlValues.required(toml, MESSAGE))),
        entry("first-argument", toml -> new FirstArgumentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "named-expr-without-context",
            toml -> new NamedExprWithoutContextCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static RuleCheck loggingFormat(final TomlParseResult toml) {
    return new LoggingFormatCheck(
        LoggingFormatCheck.Problem.valueOf(
            TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck sameModuleCall(final TomlParseResult toml) {
    return new SameModuleCallCheck(
        problemOf(toml, SameModuleCallCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck classProtocol(final TomlParseResult toml) {
    return new ClassProtocolCheck(
        problemOf(toml, ClassProtocolCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck classOverride(final TomlParseResult toml) {
    return new ClassOverrideCheck(
        problemOf(toml, ClassOverrideCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck modifiedIteration(final TomlParseResult toml) {
    return new ModifiedIterationCheck(
        ModifiedIterationCheck.Kind.valueOf(
            TomlValues.required(toml, "kind").toUpperCase(java.util.Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck nonlocal(final TomlParseResult toml) {
    return new NonlocalCheck(TomlValues.required(toml, MESSAGE));
  }

  /**
   * The enum constant named by the rule's {@code problem} option, such as no-value-for-parameter.
   */
  private static <E extends Enum<E>> E problemOf(final TomlParseResult toml, final Class<E> type) {
    return Enum.valueOf(
        type,
        TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
  }

  private static RuleCheck dunderReturn(final TomlParseResult toml) {
    return new DunderReturnCheck(
        TomlValues.required(toml, "method"),
        DunderReturnCheck.parseExpected(TomlValues.required(toml, "returns")),
        Boolean.TRUE.equals(toml.getBoolean("non-negative")),
        TomlValues.required(toml, MESSAGE));
  }
}
