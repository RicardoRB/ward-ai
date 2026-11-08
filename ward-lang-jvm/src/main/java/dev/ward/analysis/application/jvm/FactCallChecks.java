package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AnnotationImplementation;
import dev.ward.analysis.domain.rule.check.AutoFactoryInjected;
import dev.ward.analysis.domain.rule.check.AutoValueBuilderConstructor;
import dev.ward.analysis.domain.rule.check.CallFactsCheck;
import dev.ward.analysis.domain.rule.check.ComparableTypeMismatch;
import dev.ward.analysis.domain.rule.check.CompareToZeroCheck;
import dev.ward.analysis.domain.rule.check.CompatibleWithMisuse;
import dev.ward.analysis.domain.rule.check.CompileTimeConstantCheck;
import dev.ward.analysis.domain.rule.check.DeclarationFactTest;
import dev.ward.analysis.domain.rule.check.DeclarationFactsCheck;
import dev.ward.analysis.domain.rule.check.MethodOverrideCheck;
import dev.ward.analysis.domain.rule.check.ProtoStringEqualityCheck;
import dev.ward.analysis.domain.rule.check.RequiredModifiersCheck;
import dev.ward.analysis.domain.rule.check.ThreadSafeFieldsCheck;
import dev.ward.analysis.domain.types.FactKind;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks that read the annotations and supertypes the type facts give for calls and
 * method declarations, and the checks of the annotations of one file.
 */
final class FactCallChecks {

  private static final String MESSAGE = "message";

  private static final Set<String> CLASSES =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<FactKind> CLASS_FACTS = Set.of(FactKind.SHAPE, FactKind.MEMBERS);

  private static final Map<String, Declared> DECLARED =
      Map.of(
          "compatible-with-misuse",
          new Declared(
              Set.of("methodDeclaration"), Set.of(FactKind.CALL), new CompatibleWithMisuse()),
          "comparable-type-mismatch",
          new Declared(CLASSES, CLASS_FACTS, new ComparableTypeMismatch()),
          "annotation-implementation",
          new Declared(CLASSES, CLASS_FACTS, new AnnotationImplementation()),
          "auto-factory-injected",
          new Declared(Set.of("classDeclaration"), CLASS_FACTS, new AutoFactoryInjected()),
          "auto-value-builder-constructor",
          new Declared(Set.of("classDeclaration"), CLASS_FACTS, new AutoValueBuilderConstructor()));

  /** A declaration test with the node kinds it reads and the facts it needs. */
  private record Declared(Set<String> kinds, Set<FactKind> facts, DeclarationFactTest test) {}

  private FactCallChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("call-facts", FactCallChecks::callFacts),
        entry("method-override", FactCallChecks::methodOverride),
        entry("declaration-facts", FactCallChecks::declarationFacts),
        entry(
            "compare-to-zero", toml -> new CompareToZeroCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "compile-time-constant",
            toml -> new CompileTimeConstantCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "thread-safe-fields",
            toml -> new ThreadSafeFieldsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "proto-string-equality",
            toml -> new ProtoStringEqualityCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "required-modifiers",
            toml -> new RequiredModifiersCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static CallFactsCheck callFacts(final TomlParseResult toml) {
    return new CallFactsCheck(
        new CallFactsCheck.Criteria(
            TomlValues.optionalStrings(toml, "declaring-types"),
            TomlValues.optionalStrings(toml, "names"),
            TomlValues.optionalStrings(toml, "method-annotations"),
            TomlValues.optionalStrings(toml, "declaring-annotations"),
            TomlValues.optionalStrings(toml, "receiver-ancestors"),
            Pattern.compile(toml.getString("text", () -> "")),
            toml.getBoolean("ignored", () -> false),
            toml.getBoolean("outside-resource", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }

  private static DeclarationFactsCheck declarationFacts(final TomlParseResult toml) {
    final Declared declared = DECLARED.get(TomlValues.required(toml, "test"));
    if (declared == null) {
      throw new IllegalArgumentException("unknown declaration test");
    }
    return new DeclarationFactsCheck(
        declared.kinds(), declared.facts(), declared.test(), TomlValues.required(toml, MESSAGE));
  }

  private static MethodOverrideCheck methodOverride(final TomlParseResult toml) {
    return new MethodOverrideCheck(
        new MethodOverrideCheck.Criteria(
            TomlValues.strings(toml, "overridden-annotations"),
            TomlValues.optionalStrings(toml, "required-annotations"),
            toml.getBoolean("require-super-call", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }
}
