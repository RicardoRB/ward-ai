package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorSuperCheck;
import dev.ward.analysis.domain.rule.check.FieldsAtStartCheck;
import dev.ward.analysis.domain.rule.check.FinalParametersCheck;
import dev.ward.analysis.domain.rule.check.IdenticalCatchBranchesCheck;
import dev.ward.analysis.domain.rule.check.JavaEmptyAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.LocalDeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.PrematureDeclarationCheck;
import dev.ward.analysis.domain.rule.check.RedundantInterfaceCheck;
import dev.ward.analysis.domain.rule.check.SimplifyBooleanCheck;
import dev.ward.analysis.domain.rule.check.StaticImportCountCheck;
import dev.ward.analysis.domain.rule.check.StringInstantiationCheck;
import dev.ward.analysis.domain.rule.check.ThrowsExceptionCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryConstructorCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryParenthesesCheck;
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
            "throws-exception",
            toml -> new ThrowsExceptionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "fields-at-start", toml -> new FieldsAtStartCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "final-parameters",
            toml -> new FinalParametersCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "simplify-boolean-expression",
            toml -> new SimplifyBooleanCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unnecessary-constructor",
            toml -> new UnnecessaryConstructorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unnecessary-parentheses",
            toml -> new UnnecessaryParenthesesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "string-instantiation",
            toml -> new StringInstantiationCheck(TomlValues.required(toml, MESSAGE))),
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
