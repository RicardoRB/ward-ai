package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorSuperCheck;
import dev.ward.analysis.domain.rule.check.IdenticalCatchBranchesCheck;
import dev.ward.analysis.domain.rule.check.JavaEmptyAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.LocalDeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.PrematureDeclarationCheck;
import dev.ward.analysis.domain.rule.check.RedundantInterfaceCheck;
import dev.ward.analysis.domain.rule.check.StaticImportCountCheck;
import dev.ward.analysis.domain.rule.check.UselessQualifiedThisCheck;
import dev.ward.analysis.domain.rule.check.VariableCanBeInlinedCheck;
import java.util.Map;

/** Builds the checks of the Java code style rules that read the shape of a class or a block. */
final class CodestyleChecks {

  private static final String MESSAGE = "message";

  private CodestyleChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "static-import-count",
            toml ->
                new StaticImportCountCheck(
                    TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE))),
        entry(
            "call-super-in-constructor",
            toml -> new ConstructorSuperCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "empty-method-in-abstract-class",
            toml -> new JavaEmptyAbstractMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "identical-catch-branches",
            toml -> new IdenticalCatchBranchesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "local-declaration-order",
            toml -> new LocalDeclarationOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "premature-declaration",
            toml -> new PrematureDeclarationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "useless-qualified-this",
            toml -> new UselessQualifiedThisCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "variable-can-be-inlined",
            toml -> new VariableCanBeInlinedCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "redundant-interface",
            toml -> new RedundantInterfaceCheck(TomlValues.required(toml, MESSAGE))));
  }
}
