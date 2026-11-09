package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.PythonDocstringCheck;
import dev.ward.analysis.domain.rule.check.PythonImportOrderCheck;
import dev.ward.analysis.domain.rule.check.PythonMetaclassArgumentCheck;
import dev.ward.analysis.domain.rule.check.PythonOverloadOrderCheck;
import dev.ward.analysis.domain.rule.check.PythonUngroupedImportCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The declarative checks of Python's docstrings, overloads, metaclasses, import groups and variable
 * distances.
 */
final class PythonStyleChecks {

  private static final String MESSAGE = "message";
  private static final String PROBLEM = "problem";

  private PythonStyleChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build()));
  }

  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry("docstring", PythonStyleChecks::docstring),
        entry(
            "overload-order",
            toml -> new PythonOverloadOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry("metaclass-argument", PythonStyleChecks::metaclassArgument),
        entry(
            "ungrouped-imports",
            toml -> new PythonUngroupedImportCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "python-import-order",
            toml -> new PythonImportOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "python-variable-distance",
            toml ->
                new VariableDistanceCheck(
                    TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE))));
  }

  private static RuleCheck docstring(final TomlParseResult toml) {
    return new PythonDocstringCheck(
        PythonDocstringCheck.Problem.valueOf(problem(toml)), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck metaclassArgument(final TomlParseResult toml) {
    return new PythonMetaclassArgumentCheck(
        PythonMetaclassArgumentCheck.Problem.valueOf(problem(toml)),
        TomlValues.required(toml, MESSAGE));
  }

  /**
   * The {@code problem} key as an enum constant name: {@code trailing-period} becomes
   * TRAILING_PERIOD.
   */
  private static String problem(final TomlParseResult toml) {
    return TomlValues.required(toml, PROBLEM).toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
