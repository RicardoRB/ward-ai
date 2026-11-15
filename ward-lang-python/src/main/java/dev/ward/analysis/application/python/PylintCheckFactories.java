package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DunderReturnCheck;
import dev.ward.analysis.domain.rule.check.ElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.ExceptionStatementCheck;
import dev.ward.analysis.domain.rule.check.FieldCallCheck;
import dev.ward.analysis.domain.rule.check.LoggingFormatCheck;
import dev.ward.analysis.domain.rule.check.MissingParenthesesCheck;
import dev.ward.analysis.domain.rule.check.NoMethodArgumentCheck;
import dev.ward.analysis.domain.rule.check.PointlessStringCheck;
import dev.ward.analysis.domain.rule.check.PossiblyUnusedVariableCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The Pylint-derived Python checks that the rule TOMLs name, registered by their check names. */
final class PylintCheckFactories {
  private static final String MESSAGE = "message";

  private PylintCheckFactories() {}

  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(
        List.of(argumentAndReturnChecks(), PylintClassCallFactories.checks())
    );
  }

  private static Map<String, CheckFactory> argumentAndReturnChecks() {
    return Map.ofEntries(
        entry("dunder-return", PylintCheckFactories::dunderReturn),
        entry(
            "missing-parentheses",
            toml -> new MissingParenthesesCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "no-method-argument",
            toml -> new NoMethodArgumentCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "else-after-jump",
            toml ->
                new ElseAfterJumpCheck(
                    TomlValues.required(toml, "jump"),
                    TomlValues.required(toml, MESSAGE)
                )
        ),
        entry("pointless-string", toml -> new PointlessStringCheck()),
        entry(
            "possibly-unused-variable",
            toml -> new PossiblyUnusedVariableCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry("logging-format", PylintCheckFactories::loggingFormat),
        entry(
            "exception-statement",
            toml -> new ExceptionStatementCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry("field-call", toml -> new FieldCallCheck(TomlValues.required(toml, MESSAGE)))
    );
  }

  private static RuleCheck loggingFormat(final TomlParseResult toml) {
    return new LoggingFormatCheck(
        LoggingFormatCheck.Problem.valueOf(
            TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT)
        ),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck dunderReturn(final TomlParseResult toml) {
    return new DunderReturnCheck(
        TomlValues.required(toml, "method"),
        DunderReturnCheck.parseExpected(TomlValues.required(toml, "returns")),
        Boolean.TRUE.equals(toml.getBoolean("non-negative")),
        TomlValues.required(toml, MESSAGE)
    );
  }
}
