package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlLists;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AtclauseOrderCheck;
import dev.ward.analysis.domain.rule.check.JavadocTagContinuationIndentationCheck;
import dev.ward.analysis.domain.rule.check.NonEmptyAtclauseDescriptionCheck;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java Javadoc checks that a rule body names, with their parameters. Each parameter is a
 * rule option, named after the Checkstyle property in kebab-case. The {@code target} option names
 * declaration kinds by their Checkstyle token, and the tag options name block tags.
 */
final class JavadocChecks {

  private static final String MESSAGE = "message";
  private static final String TAG_ORDER = "tag-order";
  private static final String TARGET = "target";
  private static final String JAVADOC_TOKENS = "javadoc-tokens";
  private static final String OFFSET = "offset";
  private static final String MODIFIERS = "modifiers";

  private static final Map<String, String> TOKEN_KINDS =
      Map.of(
          "CLASS_DEF", "classDeclaration",
          "INTERFACE_DEF", "interfaceDeclaration",
          "ENUM_DEF", "enumDeclaration",
          "RECORD_DEF", "recordDeclaration",
          "METHOD_DEF", "methodDeclaration",
          "CTOR_DEF", "constructorDeclaration",
          "COMPACT_CTOR_DEF", "compactConstructorDeclaration",
          "VARIABLE_DEF", "fieldDeclaration");

  private JavadocChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build()));
  }

  /** Builds the Javadoc check {@code name} with the parameters of {@code toml}. */
  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry(
            "atclause-order",
            toml ->
                new AtclauseOrderCheck(
                    kinds(toml),
                    TomlValues.strings(toml, MODIFIERS),
                    TomlLists.optional(toml, TAG_ORDER),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "non-empty-atclause-description",
            toml ->
                new NonEmptyAtclauseDescriptionCheck(
                    TomlValues.strings(toml, JAVADOC_TOKENS), TomlValues.required(toml, MESSAGE))),
        entry(
            "javadoc-tag-continuation-indentation",
            toml ->
                new JavadocTagContinuationIndentationCheck(
                    TomlValues.integer(toml, OFFSET), TomlValues.required(toml, MESSAGE))));
  }

  private static Set<String> kinds(final TomlParseResult toml) {
    return TomlLists.optional(toml, TARGET).stream()
        .map(token -> TOKEN_KINDS.getOrDefault(token, token))
        .collect(Collectors.toUnmodifiableSet());
  }
}
