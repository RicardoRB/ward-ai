package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AbstractClassCheck;
import dev.ward.analysis.domain.rule.check.ArgumentOrderCheck;
import dev.ward.analysis.domain.rule.check.AssignedCallCheck;
import dev.ward.analysis.domain.rule.check.CallableComparisonCheck;
import dev.ward.analysis.domain.rule.check.ClassBaseCheck;
import dev.ward.analysis.domain.rule.check.GlobalUndefinedCheck;
import dev.ward.analysis.domain.rule.check.SignatureOverrideCheck;
import java.util.List;
import java.util.Map;

/** The Pylint checks of part A2 that are declarative rules over the syntax tree or the scopes. */
final class PylintPartA2Checks {

  private static final String MESSAGE = "message";

  private PylintPartA2Checks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(classChecks(), signatureChecks(), assignmentChecks()));
  }

  private static Map<String, CheckFactory> classChecks() {
    return Map.ofEntries(
        entry(
            "abstract-class-instantiated",
            toml ->
                new AbstractClassCheck(
                    AbstractClassCheck.Problem.INSTANTIATED, TomlValues.required(toml, MESSAGE))),
        entry(
            "abstract-method-not-overridden",
            toml ->
                new AbstractClassCheck(
                    AbstractClassCheck.Problem.METHOD_NOT_OVERRIDDEN,
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "inconsistent-mro",
            toml ->
                new ClassBaseCheck(
                    ClassBaseCheck.Problem.INCONSISTENT_MRO, TomlValues.required(toml, MESSAGE))),
        entry(
            "inherit-non-class",
            toml ->
                new ClassBaseCheck(
                    ClassBaseCheck.Problem.NON_CLASS_BASE, TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> signatureChecks() {
    return Map.ofEntries(
        entry(
            "override-signature-differs",
            toml ->
                new SignatureOverrideCheck(
                    SignatureOverrideCheck.Problem.DIFFERS, TomlValues.required(toml, MESSAGE))),
        entry(
            "override-parameter-renamed",
            toml ->
                new SignatureOverrideCheck(
                    SignatureOverrideCheck.Problem.RENAMED, TomlValues.required(toml, MESSAGE))),
        entry(
            "call-arguments-out-of-order",
            toml -> new ArgumentOrderCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> assignmentChecks() {
    return Map.ofEntries(
        entry(
            "assigned-call-no-return",
            toml ->
                new AssignedCallCheck(
                    AssignedCallCheck.Problem.NO_RETURN, TomlValues.required(toml, MESSAGE))),
        entry(
            "assigned-call-none",
            toml ->
                new AssignedCallCheck(
                    AssignedCallCheck.Problem.NONE, TomlValues.required(toml, MESSAGE))),
        entry(
            "callable-comparison",
            toml -> new CallableComparisonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "global-undefined",
            toml -> new GlobalUndefinedCheck(TomlValues.required(toml, MESSAGE))));
  }
}
