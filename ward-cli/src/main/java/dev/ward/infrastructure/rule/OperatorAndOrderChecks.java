package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.ComparisonOperatorCheck;
import dev.ward.analysis.domain.rule.check.ExceptOrderCheck;
import dev.ward.analysis.domain.rule.check.RaiseWithoutFromCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Locale;
import org.tomlj.TomlParseResult;

/** The declarative checks that read a comparison's operator or the order of a try's handlers. */
final class OperatorAndOrderChecks {

  private static final String MESSAGE = "message";

  private OperatorAndOrderChecks() {}

  /** Whether {@code name} is a check of this family. */
  static boolean isCheck(final String name) {
    return switch (name) {
      case "comparison-operator", "except-order", "raise-without-from" -> true;
      default -> false;
    };
  }

  /** Builds the check {@code name} from its table. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "comparison-operator" -> comparisonOperator(toml);
      case "except-order" -> new ExceptOrderCheck(TomlValues.required(toml, MESSAGE));
      default -> new RaiseWithoutFromCheck(TomlValues.required(toml, MESSAGE));
    };
  }

  private static RuleCheck comparisonOperator(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new ComparisonOperatorCheck(
        ComparisonOperatorCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }
}
