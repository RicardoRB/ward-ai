package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.ReassignedBindingCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.UndefinedNameCheck;
import dev.ward.analysis.domain.rule.check.UnreachableCodeCheck;
import dev.ward.analysis.domain.rule.check.UnusedBindingCheck;
import java.util.Set;
import org.tomlj.TomlParseResult;

/** Builds the checks that read scopes or control flow, and hands the other names on. */
final class ScopeChecks {

  private static final String MESSAGE = "message";

  private ScopeChecks() {}

  /** Builds a check that reads scopes or control flow, or else the remaining checks. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "undefined-name" ->
          new UndefinedNameCheck(
              TomlValues.required(toml, MESSAGE),
              Set.copyOf(TomlValues.optionalStrings(toml, "env")));
      case "unused-binding" -> new UnusedBindingCheck(TomlValues.required(toml, MESSAGE));
      case "unreachable-code" -> new UnreachableCodeCheck(TomlValues.required(toml, MESSAGE));
      case "reassigned-binding" ->
          ReassignedBindingCheck.of(
              TomlValues.strings(toml, "binding-kinds"), TomlValues.required(toml, MESSAGE));
      default ->
          ScopeAndFormatChecks.isCheck(name)
              ? ScopeAndFormatChecks.build(name, toml)
              : JavadocChecks.isJavadocCheck(name)
                  ? JavadocChecks.build(name, toml)
                  : ImportChecks.isImportCheck(name)
                      ? ImportChecks.build(name, toml)
                      : ReviewChecks.build(name, toml);
    };
  }
}
