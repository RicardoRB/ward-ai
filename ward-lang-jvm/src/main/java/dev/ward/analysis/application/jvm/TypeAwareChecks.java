package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AccessorClassGenerationCheck;
import dev.ward.analysis.domain.rule.check.AccessorMethodGenerationCheck;
import dev.ward.analysis.domain.rule.check.CheckResultSetCheck;
import dev.ward.analysis.domain.rule.check.EnumComparisonCheck;
import dev.ward.analysis.domain.rule.check.GuardLogStatementCheck;
import dev.ward.analysis.domain.rule.check.UseEnumCollectionsCheck;
import java.util.List;
import java.util.Map;

/**
 * The checks of the Java rules that read the Java release or the type facts of a file. A check
 * whose type facts are unknown reports nothing, and a release of zero means unknown.
 */
final class TypeAwareChecks {

  private static final String MESSAGE = "message";
  private static final String JAVA_RELEASE = "java-release";

  private TypeAwareChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(release(), types()));
  }

  private static Map<String, CheckFactory> release() {
    return Map.ofEntries(
        entry(
            "accessor-class-generation",
            toml ->
                new AccessorClassGenerationCheck(
                    TomlValues.integer(toml, JAVA_RELEASE), TomlValues.required(toml, MESSAGE))),
        entry(
            "accessor-method-generation",
            toml ->
                new AccessorMethodGenerationCheck(
                    TomlValues.integer(toml, JAVA_RELEASE), TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> types() {
    return Map.ofEntries(
        entry(
            "check-result-set",
            toml -> new CheckResultSetCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "enum-comparison", toml -> new EnumComparisonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "use-enum-collections",
            toml -> new UseEnumCollectionsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "guard-log-statement",
            toml -> new GuardLogStatementCheck(TomlValues.required(toml, MESSAGE))));
  }
}
