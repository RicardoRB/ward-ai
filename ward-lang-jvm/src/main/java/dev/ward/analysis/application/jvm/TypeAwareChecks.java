package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AccessorClassGenerationCheck;
import dev.ward.analysis.domain.rule.check.AccessorMethodGenerationCheck;
import dev.ward.analysis.domain.rule.check.ArrayReceiverCallCheck;
import dev.ward.analysis.domain.rule.check.CallMatchCheck;
import dev.ward.analysis.domain.rule.check.CallMatchContext;
import dev.ward.analysis.domain.rule.check.CheckResultSetCheck;
import dev.ward.analysis.domain.rule.check.ConditionalPromotionCheck;
import dev.ward.analysis.domain.rule.check.ConstantOverflowCheck;
import dev.ward.analysis.domain.rule.check.EnumComparisonCheck;
import dev.ward.analysis.domain.rule.check.ExhaustiveSwitchHasDefaultCheck;
import dev.ward.analysis.domain.rule.check.GuardLogStatementCheck;
import dev.ward.analysis.domain.rule.check.IncompatibleArgumentCheck;
import dev.ward.analysis.domain.rule.check.NonExhaustiveSwitchCheck;
import dev.ward.analysis.domain.rule.check.ReferenceEqualityCheck;
import dev.ward.analysis.domain.rule.check.SynchronizedOnTypeCheck;
import dev.ward.analysis.domain.rule.check.UseEnumCollectionsCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * The checks of the Java rules that read the Java release or the type facts of a file. A check
 * whose type facts are unknown reports nothing, and a release of zero means unknown.
 */
final class TypeAwareChecks {

  private static final String MESSAGE = "message";
  private static final String TYPES = "types";
  private static final String JAVA_RELEASE = "java-release";
  private static final int NAME = 0;
  private static final int ARITY = 1;
  private static final int SOURCE = 2;
  private static final int VALUE = 3;

  private TypeAwareChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(release(), types(), calls()));
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
            "array-receiver-call",
            toml ->
                new ArrayReceiverCallCheck(
                    TomlValues.required(toml, "method"),
                    TomlValues.integer(toml, "arguments"),
                    TomlValues.required(toml, MESSAGE))),
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
            toml -> new GuardLogStatementCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "exhaustive-switch-has-default",
            toml -> new ExhaustiveSwitchHasDefaultCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-exhaustive-switch",
            toml -> new NonExhaustiveSwitchCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> calls() {
    return Map.ofEntries(
        entry("call-match", TypeAwareChecks::callMatch),
        entry("incompatible-argument", TypeAwareChecks::incompatibleArgument),
        entry(
            "conditional-promotion",
            toml -> new ConditionalPromotionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "constant-overflow",
            toml -> new ConstantOverflowCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "reference-equality",
            toml ->
                new ReferenceEqualityCheck(
                    TomlValues.strings(toml, TYPES), TomlValues.required(toml, MESSAGE))),
        entry(
            "synchronized-on-type",
            toml ->
                new SynchronizedOnTypeCheck(
                    TomlValues.strings(toml, TYPES), TomlValues.required(toml, MESSAGE))));
  }

  /**
   * Reads each {@code methods} entry {@code name/arity:source:value}, a source being {@code r0} or
   * {@code a0}.
   */
  private static IncompatibleArgumentCheck incompatibleArgument(final TomlParseResult toml) {
    final List<IncompatibleArgumentCheck.Entry> entries = new ArrayList<>();
    for (final String text : TomlValues.strings(toml, "methods")) {
      final String[] parts = text.split("[/:]");
      entries.add(
          new IncompatibleArgumentCheck.Entry(
              parts[NAME],
              Integer.parseInt(parts[ARITY]),
              parts[SOURCE].startsWith("r"),
              Integer.parseInt(parts[SOURCE].substring(1)),
              Integer.parseInt(parts[VALUE])));
    }
    return new IncompatibleArgumentCheck(
        TomlValues.strings(toml, "declaring-types"), entries, TomlValues.required(toml, MESSAGE));
  }

  private static CallMatchCheck callMatch(final TomlParseResult toml) {
    final CallMatchCheck.Criteria criteria =
        new CallMatchCheck.Criteria(
            TomlValues.optionalStrings(toml, "declaring-types"),
            TomlValues.optionalStrings(toml, "names"),
            TomlValues.optionalStrings(toml, "receiver-types"),
            toml.getBoolean("receiver-array", () -> false),
            Pattern.compile(toml.getString("text", () -> "")),
            toml.getBoolean("ignored", () -> false),
            TomlValues.optionalStrings(toml, "argument-types"),
            TomlValues.optionalStrings(toml, "any-argument-types"),
            toml.getBoolean("argument-primitive-array", () -> false));
    final CallMatchContext context =
        new CallMatchContext(
            TomlValues.optionalStrings(toml, "inside"),
            TomlValues.optionalStrings(toml, "not-inside"));
    return new CallMatchCheck(criteria, context, TomlValues.required(toml, MESSAGE));
  }
}
