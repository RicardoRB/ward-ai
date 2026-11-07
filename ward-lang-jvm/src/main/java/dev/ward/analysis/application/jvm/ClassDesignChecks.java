package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DataClassCheck;
import dev.ward.analysis.domain.rule.check.GodClassCheck;
import dev.ward.analysis.domain.rule.check.InternalApiUsageCheck;
import dev.ward.analysis.domain.rule.check.InvalidJavaBeanCheck;
import dev.ward.analysis.domain.rule.check.LawOfDemeterCheck;
import java.util.List;
import java.util.Map;

/**
 * Builds the class-level design checks, the PMD metrics and bean rules, and the internal JDK API
 * check. Each parameter is a rule option, named after the PMD property in kebab-case.
 */
final class ClassDesignChecks {

  private static final String MESSAGE = "message";
  private static final String PACKAGES = "packages";

  private ClassDesignChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("god-class", toml -> new GodClassCheck(TomlValues.required(toml, MESSAGE))),
        entry("data-class", toml -> new DataClassCheck(TomlValues.required(toml, MESSAGE))),
        entry("law-of-demeter", toml -> new LawOfDemeterCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "invalid-java-bean",
            toml ->
                new InvalidJavaBeanCheck(
                    List.copyOf(TomlValues.optionalStrings(toml, PACKAGES)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "internal-api-usage",
            toml -> new InternalApiUsageCheck(TomlValues.required(toml, MESSAGE))));
  }
}
