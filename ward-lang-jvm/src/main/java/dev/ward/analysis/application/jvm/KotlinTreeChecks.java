package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.KotlinLoopJumpCheck;
import dev.ward.analysis.domain.rule.check.KotlinMayBeConstCheck;
import dev.ward.analysis.domain.rule.check.KotlinUnnecessaryApplyCheck;
import dev.ward.analysis.domain.rule.check.KotlinUtilityClassCheck;
import java.util.Map;

/** Builds the Kotlin checks of detekt's style set that need the place of a node in the tree. */
final class KotlinTreeChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final int DEFAULT_MAX = 1;

  private KotlinTreeChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-may-be-const",
            toml -> new KotlinMayBeConstCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unnecessary-apply",
            toml -> new KotlinUnnecessaryApplyCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-utility-class",
            toml -> new KotlinUtilityClassCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-loop-jumps",
            toml ->
                new KotlinLoopJumpCheck(
                    TomlValues.required(toml, MESSAGE),
                    TomlValues.optionalInteger(toml, MAX, DEFAULT_MAX))));
  }
}
