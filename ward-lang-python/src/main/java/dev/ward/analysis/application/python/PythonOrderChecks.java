package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ComparisonOperatorCheck;
import dev.ward.analysis.domain.rule.check.DuplicateHandlerCheck;
import dev.ward.analysis.domain.rule.check.EmptyAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.ExceptOrderCheck;
import dev.ward.analysis.domain.rule.check.FStringDocstringCheck;
import dev.ward.analysis.domain.rule.check.FinallyJumpCheck;
import dev.ward.analysis.domain.rule.check.LoopClosureCheck;
import dev.ward.analysis.domain.rule.check.LoopVariableCheck;
import dev.ward.analysis.domain.rule.check.RaiseWithoutFromCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SetDuplicatesCheck;
import dev.ward.analysis.domain.rule.check.UnboundLocalCheck;
import dev.ward.analysis.domain.rule.check.UndefinedExportCheck;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The declarative checks of Python's comparisons, handlers, loop variables and module-level names.
 */
final class PythonOrderChecks {

  private static final String MESSAGE = "message";
  private static final String PROBLEM = "problem";

  private PythonOrderChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build()));
  }

  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry("comparison-operator", PythonOrderChecks::comparisonOperator),
        entry("loop-variable", PythonOrderChecks::loopVariable),
        entry("except-order", toml -> new ExceptOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "raise-without-from",
            toml -> new RaiseWithoutFromCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "undefined-export",
            toml -> new UndefinedExportCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "duplicate-handler",
            toml -> new DuplicateHandlerCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "fstring-docstring",
            toml -> new FStringDocstringCheck(TomlValues.required(toml, MESSAGE))),
        entry("unbound-local", toml -> new UnboundLocalCheck(TomlValues.required(toml, MESSAGE))),
        entry("loop-closure", toml -> new LoopClosureCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "empty-abstract-method",
            toml -> new EmptyAbstractMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry("finally-jump", toml -> new FinallyJumpCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "set-duplicates", toml -> new SetDuplicatesCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static RuleCheck comparisonOperator(final TomlParseResult toml) {
    return new ComparisonOperatorCheck(
        ComparisonOperatorCheck.Problem.valueOf(problem(toml)), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck loopVariable(final TomlParseResult toml) {
    return new LoopVariableCheck(
        LoopVariableCheck.Problem.valueOf(problem(toml)), TomlValues.required(toml, MESSAGE));
  }

  /** The {@code problem} key as an enum constant name: {@code not-in} becomes {@code NOT_IN}. */
  private static String problem(final TomlParseResult toml) {
    return TomlValues.required(toml, PROBLEM).toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
