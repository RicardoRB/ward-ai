package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.FinalLocalVariableCheck;
import dev.ward.analysis.domain.rule.check.IllegalInstantiationCheck;
import dev.ward.analysis.domain.rule.check.IllegalTypeCheck;
import dev.ward.analysis.domain.rule.check.PatternVariableAssignmentCheck;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java declaration checks of the strict preset: member order, illegal types and
 * instantiations, final locals and pattern variables. Each parameter is a rule option, named after
 * the Checkstyle property in kebab-case.
 */
final class StrictDeclarationChecks {

  private static final String MESSAGE = "message";

  /** Checkstyle's default class names of IllegalType, simple and qualified. */
  private static final Set<String> ILLEGAL_TYPE_DEFAULTS =
      Set.of(
          "HashMap",
          "HashSet",
          "LinkedHashMap",
          "LinkedHashSet",
          "TreeMap",
          "TreeSet",
          "java.util.HashMap",
          "java.util.HashSet",
          "java.util.LinkedHashMap",
          "java.util.LinkedHashSet",
          "java.util.TreeMap",
          "java.util.TreeSet");

  private StrictDeclarationChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "declaration-order",
            toml -> new DeclarationOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry("illegal-type", StrictDeclarationChecks::illegalType),
        entry(
            "illegal-instantiation",
            toml ->
                new IllegalInstantiationCheck(
                    TomlValues.optionalStrings(toml, "classes"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "final-local-variable",
            toml -> new FinalLocalVariableCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "pattern-variable-assignment",
            toml -> new PatternVariableAssignmentCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static IllegalTypeCheck illegalType(final TomlParseResult toml) {
    final Set<String> configured = TomlValues.optionalStrings(toml, "illegal-class-names");
    return new IllegalTypeCheck(
        configured.isEmpty() ? ILLEGAL_TYPE_DEFAULTS : configured,
        TomlValues.required(toml, MESSAGE));
  }
}
