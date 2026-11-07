package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
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
import dev.ward.analysis.domain.rule.check.SingularFieldCheck;
import dev.ward.analysis.domain.rule.check.SwitchDensityCheck;
import dev.ward.analysis.domain.rule.check.UtilityClassCheck;
import java.util.List;
import java.util.Map;

/**
 * Builds the checks of the PMD design rules, which read class members, imports, switch statements
 * and try blocks. Each parameter is a rule option, named after the PMD property in kebab-case.
 */
final class DesignChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final String MINIMUM = "minimum";

  private DesignChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(limits(), members()));
  }

  private static Map<String, CheckFactory> limits() {
    return Map.ofEntries(
        entry(
            "nested-if",
            toml ->
                new NestedIfCheck(
                    TomlValues.integer(toml, "problem-depth"), TomlValues.required(toml, MESSAGE))),
        entry(
            "import-count",
            toml ->
                new ImportCountCheck(
                    TomlValues.integer(toml, MINIMUM), TomlValues.required(toml, MESSAGE))),
        entry(
            "parameter-limit",
            toml ->
                new ParameterCountCheck(
                    TomlValues.integer(toml, MINIMUM), TomlValues.required(toml, MESSAGE))),
        entry(
            "field-limit",
            toml ->
                new FieldLimitCheck(
                    TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))),
        entry(
            "method-limit",
            toml ->
                new MethodLimitCheck(
                    TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))),
        entry(
            "public-count",
            toml ->
                new PublicCountCheck(
                    TomlValues.integer(toml, MINIMUM), TomlValues.required(toml, MESSAGE))),
        entry(
            "switch-density",
            toml ->
                new SwitchDensityCheck(
                    TomlValues.integer(toml, MINIMUM), TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> members() {
    return Map.ofEntries(
        entry(
            "protected-in-final",
            toml -> new ProtectedInFinalCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "public-in-non-public",
            toml -> new PublicInNonPublicCheck(TomlValues.required(toml, MESSAGE))),
        entry("exception-flow", toml -> new ExceptionFlowCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "immutable-field", toml -> new ImmutableFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry("singular-field", toml -> new SingularFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry("utility-class", toml -> new UtilityClassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "package-coupling",
            toml ->
                new PackageCouplingCheck(
                    List.copyOf(TomlValues.optionalStrings(toml, "packages")),
                    TomlValues.required(toml, MESSAGE))));
  }
}
