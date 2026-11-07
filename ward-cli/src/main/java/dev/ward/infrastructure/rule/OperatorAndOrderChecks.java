package dev.ward.infrastructure.rule;

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
import java.util.Locale;
import java.util.Set;
import org.tomlj.TomlParseResult;

/** The declarative checks that read a comparison's operator or the order of a try's handlers. */
final class OperatorAndOrderChecks {

  private static final String MESSAGE = "message";
  private static final Set<String> NAMES =
      Set.of(
          "comparison-operator",
          "except-order",
          "raise-without-from",
          "loop-variable",
          "undefined-export",
          "duplicate-handler",
          "fstring-docstring",
          "unbound-local",
          "loop-closure",
          "empty-abstract-method",
          "finally-jump",
          "set-duplicates");

  private OperatorAndOrderChecks() {}

  /** Whether {@code name} is a check of this family. */
  static boolean isCheck(final String name) {
    return NAMES.contains(name);
  }

  /** Builds the check {@code name} from its table. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "comparison-operator" -> comparisonOperator(toml);
      case "except-order" -> new ExceptOrderCheck(TomlValues.required(toml, MESSAGE));
      case "loop-variable" -> loopVariable(toml);
      case "undefined-export" -> new UndefinedExportCheck(TomlValues.required(toml, MESSAGE));
      case "duplicate-handler" -> new DuplicateHandlerCheck(TomlValues.required(toml, MESSAGE));
      case "fstring-docstring" -> new FStringDocstringCheck(TomlValues.required(toml, MESSAGE));
      default -> buildOther(name, toml);
    };
  }

  private static RuleCheck buildOther(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "unbound-local" -> new UnboundLocalCheck(TomlValues.required(toml, MESSAGE));
      case "loop-closure" -> new LoopClosureCheck(TomlValues.required(toml, MESSAGE));
      case "empty-abstract-method" ->
          new EmptyAbstractMethodCheck(TomlValues.required(toml, MESSAGE));
      case "finally-jump" -> new FinallyJumpCheck(TomlValues.required(toml, MESSAGE));
      case "set-duplicates" -> new SetDuplicatesCheck(TomlValues.required(toml, MESSAGE));
      default -> new RaiseWithoutFromCheck(TomlValues.required(toml, MESSAGE));
    };
  }

  private static RuleCheck loopVariable(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new LoopVariableCheck(
        LoopVariableCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck comparisonOperator(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new ComparisonOperatorCheck(
        ComparisonOperatorCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }
}
