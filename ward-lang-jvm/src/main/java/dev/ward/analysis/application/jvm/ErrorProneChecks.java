package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AssignmentToNonFinalStaticCheck;
import dev.ward.analysis.domain.rule.check.ConstructorCallsOverridableMethodCheck;
import dev.ward.analysis.domain.rule.check.DetachedTestCaseCheck;
import dev.ward.analysis.domain.rule.check.JumbledIncrementerCheck;
import dev.ward.analysis.domain.rule.check.MemberNamedAfterClassCheck;
import dev.ward.analysis.domain.rule.check.ReturnEmptyCollectionCheck;
import dev.ward.analysis.domain.rule.check.SingleMethodSingletonCheck;
import dev.ward.analysis.domain.rule.check.StaticEjbFieldCheck;
import java.util.Map;

/** Builds the class-level checks of the PMD error-prone rules that read the members of a class. */
final class ErrorProneChecks {

  private static final String MESSAGE = "message";
  private static final String MEMBER = "member";

  private ErrorProneChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "member-named-after-class",
            toml ->
                new MemberNamedAfterClassCheck(
                    TomlValues.required(toml, MEMBER), TomlValues.required(toml, MESSAGE))),
        entry(
            "return-empty-collection",
            toml -> new ReturnEmptyCollectionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "static-ejb-field",
            toml -> new StaticEjbFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "single-method-singleton",
            toml -> new SingleMethodSingletonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "assignment-to-non-final-static",
            toml -> new AssignmentToNonFinalStaticCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "constructor-calls-overridable-method",
            toml -> new ConstructorCallsOverridableMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "detached-test-case",
            toml -> new DetachedTestCaseCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "jumbled-incrementer",
            toml -> new JumbledIncrementerCheck(TomlValues.required(toml, MESSAGE))));
  }
}
