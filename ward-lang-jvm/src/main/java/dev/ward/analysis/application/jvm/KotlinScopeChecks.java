package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinNestedClassesVisibilityCheck;
import dev.ward.analysis.domain.rule.check.KotlinUnusedParameterCheck;
import dev.ward.analysis.domain.rule.check.KotlinUnusedPrivateClassCheck;
import dev.ward.analysis.domain.rule.check.KotlinUnusedPrivateMemberCheck;
import dev.ward.analysis.domain.rule.check.KotlinVarCouldBeValCheck;
import java.util.Map;

/** Builds the Kotlin checks of detekt's style set that read the scopes of a unit. */
final class KotlinScopeChecks {

  private static final String MESSAGE = "message";

  private KotlinScopeChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-unused-parameter",
            toml -> new KotlinUnusedParameterCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unused-private-member",
            toml -> new KotlinUnusedPrivateMemberCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unused-private-class",
            toml -> new KotlinUnusedPrivateClassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-var-could-be-val",
            toml -> new KotlinVarCouldBeValCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-nested-classes-visibility",
            toml -> new KotlinNestedClassesVisibilityCheck(TomlValues.required(toml, MESSAGE))));
  }
}
