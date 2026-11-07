package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ObjectMethodCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks of the {@code clone} and {@code finalize} rules: methods that must not exist,
 * and methods that must call their {@code super} method.
 */
final class ObjectMethodChecks {

  private static final String MESSAGE = "message";
  private static final String CLONE = "clone";
  private static final String FINALIZE = "finalize";

  private ObjectMethodChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("no-clone", toml -> check(CLONE, false, toml)),
        entry("no-finalizer", toml -> check(FINALIZE, false, toml)),
        entry("super-clone", toml -> check(CLONE, true, toml)),
        entry("super-finalize", toml -> check(FINALIZE, true, toml)));
  }

  private static ObjectMethodCheck check(
      final String name, final boolean requireSuperCall, final TomlParseResult toml) {
    return new ObjectMethodCheck(name, requireSuperCall, TomlValues.required(toml, MESSAGE));
  }
}
