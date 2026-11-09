package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.GlobalVariableNotAssignedCheck;
import dev.ward.analysis.domain.rule.check.ImportSelfCheck;
import dev.ward.analysis.domain.rule.check.RedefinedArgumentFromLocalCheck;
import dev.ward.analysis.domain.rule.check.RedefinedSlotsInSubclassCheck;
import dev.ward.analysis.domain.rule.check.StopIterationReturnCheck;
import dev.ward.analysis.domain.rule.check.TooManyLocalsCheck;
import dev.ward.analysis.domain.rule.check.TooManyPublicMethodsCheck;
import dev.ward.analysis.domain.rule.check.UselessElseOnLoopCheck;
import dev.ward.analysis.domain.rule.check.UselessReturnCheck;
import java.util.Map;

/**
 * The declarative checks of the Ruff general rules that mirror Pylint: loops, returns and scopes.
 */
final class PythonGeneralChecks {

  private static final String MESSAGE = "message";

  private PythonGeneralChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "useless-else-on-loop",
            toml -> new UselessElseOnLoopCheck(TomlValues.required(toml, MESSAGE))),
        entry("useless-return", toml -> new UselessReturnCheck(TomlValues.required(toml, MESSAGE))),
        entry("import-self", toml -> new ImportSelfCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "global-variable-not-assigned",
            toml -> new GlobalVariableNotAssignedCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "stop-iteration-return",
            toml -> new StopIterationReturnCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "redefined-argument-from-local",
            toml -> new RedefinedArgumentFromLocalCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "redefined-slots-in-subclass",
            toml -> new RedefinedSlotsInSubclassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "too-many-locals",
            toml ->
                new TooManyLocalsCheck(
                    TomlValues.integer(toml, "max-locals"), TomlValues.required(toml, MESSAGE))),
        entry(
            "too-many-public-methods",
            toml ->
                new TooManyPublicMethodsCheck(
                    TomlValues.integer(toml, "max-public-methods"),
                    TomlValues.required(toml, MESSAGE))));
  }
}
