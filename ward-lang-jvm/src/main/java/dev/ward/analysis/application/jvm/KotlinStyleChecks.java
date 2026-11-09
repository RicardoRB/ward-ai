package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinConstructorGroupingCheck;
import dev.ward.analysis.domain.rule.check.KotlinHiddenFieldCheck;
import dev.ward.analysis.domain.rule.check.KotlinKdocMethodCheck;
import dev.ward.analysis.domain.rule.check.KotlinKdocPositionCheck;
import dev.ward.analysis.domain.rule.check.KotlinMagicNumberCheck;
import dev.ward.analysis.domain.rule.check.KotlinMissingKdocTypeCheck;
import dev.ward.analysis.domain.rule.check.KotlinOneStatementPerLineCheck;
import dev.ward.analysis.domain.rule.check.KotlinOverloadOrderCheck;
import dev.ward.analysis.domain.rule.check.KotlinUsageDistanceCheck;
import java.util.Map;

/** Builds the Kotlin checks of the Checkstyle concepts and KDoc rules that read the tree. */
final class KotlinStyleChecks {

  private static final String MESSAGE = "message";

  private KotlinStyleChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-constructor-grouping",
            toml -> new KotlinConstructorGroupingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-hidden-field",
            toml -> new KotlinHiddenFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-kdoc-method",
            toml -> new KotlinKdocMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-kdoc-position",
            toml -> new KotlinKdocPositionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-magic-number",
            toml -> new KotlinMagicNumberCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-missing-kdoc-type",
            toml -> new KotlinMissingKdocTypeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-one-statement-per-line",
            toml -> new KotlinOneStatementPerLineCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-overload-order",
            toml -> new KotlinOverloadOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-usage-distance",
            toml -> new KotlinUsageDistanceCheck(TomlValues.required(toml, MESSAGE))));
  }
}
