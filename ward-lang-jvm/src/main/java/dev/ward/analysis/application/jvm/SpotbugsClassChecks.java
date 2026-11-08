package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AmbiguousInvocationCheck;
import dev.ward.analysis.domain.rule.check.EqualsInheritanceCheck;
import dev.ward.analysis.domain.rule.check.IncompatibleEqualsOperandCheck;
import dev.ward.analysis.domain.rule.check.IntegerArithmeticCheck;
import dev.ward.analysis.domain.rule.check.LazyStaticInitCheck;
import dev.ward.analysis.domain.rule.check.ParameterStringComparisonCheck;
import dev.ward.analysis.domain.rule.check.StaticClassInstantiationCheck;
import dev.ward.analysis.domain.rule.check.StaticInitializationCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The checks of the SpotBugs rules for Java that read classes, their members and arithmetic. */
final class SpotbugsClassChecks {

  private static final String MESSAGE = "message";

  private SpotbugsClassChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "equals-inheritance",
            toml ->
                new EqualsInheritanceCheck(
                    EqualsInheritanceCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "ambiguous-invocation",
            toml -> new AmbiguousInvocationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "incompatible-equals-operand",
            toml -> new IncompatibleEqualsOperandCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "parameter-string-comparison",
            toml -> new ParameterStringComparisonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "lazy-static-init",
            toml ->
                new LazyStaticInitCheck(
                    LazyStaticInitCheck.Mode.valueOf(mode(toml)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "static-initialization",
            toml ->
                new StaticInitializationCheck(
                    StaticInitializationCheck.Mode.valueOf(mode(toml)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "static-class-instantiation",
            toml -> new StaticClassInstantiationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "integer-arithmetic",
            toml ->
                new IntegerArithmeticCheck(
                    IntegerArithmeticCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static String mode(final TomlParseResult toml) {
    return TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT);
  }
}
