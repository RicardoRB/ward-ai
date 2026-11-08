package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.JavaAlwaysThrowsCheck;
import dev.ward.analysis.domain.rule.check.JavaAutoValueOrderCheck;
import dev.ward.analysis.domain.rule.check.JavaBundleCastCheck;
import dev.ward.analysis.domain.rule.check.JavaChainingConstructorCheck;
import java.util.Map;

/** The checks of the last Error Prone standard ports for Java: calls that always fail, orders. */
final class ErrorProneLastChecks {

  private static final String MESSAGE = "message";

  private ErrorProneLastChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "always-throws", toml -> new JavaAlwaysThrowsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "auto-value-order",
            toml -> new JavaAutoValueOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "bundle-deserialization-cast",
            toml -> new JavaBundleCastCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "chaining-constructor",
            toml -> new JavaChainingConstructorCheck(TomlValues.required(toml, MESSAGE))));
  }
}
