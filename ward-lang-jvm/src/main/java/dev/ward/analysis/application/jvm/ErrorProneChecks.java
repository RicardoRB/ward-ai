package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AnnotationStructureCheck;
import dev.ward.analysis.domain.rule.check.AssignmentToNonFinalStaticCheck;
import dev.ward.analysis.domain.rule.check.ConstructorCallsOverridableMethodCheck;
import dev.ward.analysis.domain.rule.check.DetachedTestCaseCheck;
import dev.ward.analysis.domain.rule.check.JumbledIncrementerCheck;
import dev.ward.analysis.domain.rule.check.MemberNamedAfterClassCheck;
import dev.ward.analysis.domain.rule.check.ReturnEmptyCollectionCheck;
import dev.ward.analysis.domain.rule.check.SingleMethodSingletonCheck;
import dev.ward.analysis.domain.rule.check.StaticEjbFieldCheck;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** Builds the class-level checks of the PMD error-prone rules that read the members of a class. */
final class ErrorProneChecks {

  private static final String MESSAGE = "message";
  private static final String MEMBER = "member";
  private static final String MEMBERS = "members";

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
        entry("annotation-structure", ErrorProneChecks::annotationStructure),
        entry(
            "jumbled-incrementer",
            toml -> new JumbledIncrementerCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static AnnotationStructureCheck annotationStructure(final TomlParseResult toml) {
    final List<Pattern> has =
        TomlValues.optionalStrings(toml, "class-has").stream()
            .sorted()
            .map(Pattern::compile)
            .toList();
    final String lacks = toml.getString("class-lacks", () -> "");
    final String members = toml.getString(MEMBERS, () -> "");
    return new AnnotationStructureCheck(
        new AnnotationStructureCheck.Criteria(
            TomlValues.optionalStrings(toml, "types"),
            has,
            lacks.isEmpty() ? null : Pattern.compile(lacks),
            toml.getBoolean("enclosing", () -> false),
            members.isEmpty() ? null : Pattern.compile(members),
            toml.getString("member-kind", () -> ""),
            (int) toml.getLong("least", () -> 0L),
            (int) toml.getLong("most", () -> -1L),
            toml.getBoolean("report-members", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }
}
