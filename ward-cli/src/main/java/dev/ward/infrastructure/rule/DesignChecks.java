package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.ExceptionFlowCheck;
import dev.ward.analysis.domain.rule.check.FieldLimitCheck;
import dev.ward.analysis.domain.rule.check.ImmutableFieldCheck;
import dev.ward.analysis.domain.rule.check.ImportCountCheck;
import dev.ward.analysis.domain.rule.check.MethodLimitCheck;
import dev.ward.analysis.domain.rule.check.NestedIfCheck;
import dev.ward.analysis.domain.rule.check.PackageCouplingCheck;
import dev.ward.analysis.domain.rule.check.ParameterCountCheck;
import dev.ward.analysis.domain.rule.check.ProtectedInFinalCheck;
import dev.ward.analysis.domain.rule.check.PublicCountCheck;
import dev.ward.analysis.domain.rule.check.PublicInNonPublicCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SingularFieldCheck;
import dev.ward.analysis.domain.rule.check.SwitchDensityCheck;
import dev.ward.analysis.domain.rule.check.UtilityClassCheck;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlParseResult;

/** Builds the checks of the design rules, which read class members, imports and try blocks. */
final class DesignChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final String MINIMUM = "minimum";

  private static final Set<String> LIMITS =
      Set.of(
          "nested-if",
          "import-count",
          "parameter-limit",
          "field-limit",
          "method-limit",
          "public-count",
          "switch-density");

  private static final Set<String> NAMES =
      Set.of(
          "nested-if",
          "import-count",
          "parameter-limit",
          "field-limit",
          "method-limit",
          "public-count",
          "protected-in-final",
          "public-in-non-public",
          "switch-density",
          "exception-flow",
          "immutable-field",
          "singular-field",
          "utility-class",
          "package-coupling");

  private DesignChecks() {}

  /** Tells whether {@code name} is one of the design checks. */
  static boolean isCheck(final String name) {
    return NAMES.contains(name);
  }

  /** Builds the design check {@code name}, with its parameters read from {@code toml}. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    final String message = TomlValues.required(toml, MESSAGE);
    return LIMITS.contains(name) ? limit(name, toml, message) : member(name, toml, message);
  }

  private static RuleCheck limit(
      final String name, final TomlParseResult toml, final String message) {
    return switch (name) {
      case "nested-if" -> new NestedIfCheck(TomlValues.integer(toml, "problem-depth"), message);
      case "import-count" -> new ImportCountCheck(TomlValues.integer(toml, MINIMUM), message);
      case "parameter-limit" -> new ParameterCountCheck(TomlValues.integer(toml, MINIMUM), message);
      case "field-limit" -> new FieldLimitCheck(TomlValues.integer(toml, MAX), message);
      case "method-limit" -> new MethodLimitCheck(TomlValues.integer(toml, MAX), message);
      case "public-count" -> new PublicCountCheck(TomlValues.integer(toml, MINIMUM), message);
      default -> new SwitchDensityCheck(TomlValues.integer(toml, MINIMUM), message);
    };
  }

  private static RuleCheck member(
      final String name, final TomlParseResult toml, final String message) {
    return switch (name) {
      case "protected-in-final" -> new ProtectedInFinalCheck(message);
      case "public-in-non-public" -> new PublicInNonPublicCheck(message);
      case "exception-flow" -> new ExceptionFlowCheck(message);
      case "immutable-field" -> new ImmutableFieldCheck(message);
      case "singular-field" -> new SingularFieldCheck(message);
      case "utility-class" -> new UtilityClassCheck(message);
      default ->
          new PackageCouplingCheck(
              List.copyOf(TomlValues.optionalStrings(toml, "packages")), message);
    };
  }
}
