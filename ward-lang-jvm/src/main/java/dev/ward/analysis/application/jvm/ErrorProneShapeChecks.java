package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.JavaInjectMemberConstructorCheck;
import dev.ward.analysis.domain.rule.check.JavaJUnit3TestNotRunCheck;
import dev.ward.analysis.domain.rule.check.JavaJUnitParameterMethodCheck;
import dev.ward.analysis.domain.rule.check.JavaLoopConditionCheck;
import java.util.Map;

/**
 * Builds the checks of Error Prone rules that read the members of a class or the body of a loop.
 */
final class ErrorProneShapeChecks {

  private static final String MESSAGE = "message";

  private ErrorProneShapeChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "inject-on-member-and-constructor",
            toml -> new JavaInjectMemberConstructorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "junit3-test-not-run",
            toml -> new JavaJUnit3TestNotRunCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "junit-parameter-method",
            toml -> new JavaJUnitParameterMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "loop-condition",
            toml -> new JavaLoopConditionCheck(TomlValues.required(toml, MESSAGE))));
  }
}
