package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.OverridingThreadRunCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of the thread rules that read a class's superclass. */
final class ThreadChecks {

  private static final String MESSAGE = "message";

  private ThreadChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(entry("overriding-thread-run", ThreadChecks::overridingThreadRun));
  }

  private static OverridingThreadRunCheck overridingThreadRun(final TomlParseResult toml) {
    return new OverridingThreadRunCheck(TomlValues.required(toml, MESSAGE));
  }
}
