package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.IntLimitComparisonCheck;
import dev.ward.analysis.domain.rule.check.LazyInitializationCheck;
import dev.ward.analysis.domain.rule.check.TransientInPlainClassCheck;
import java.util.Map;

/** The checks that read the declarations around a node, so that a rule skips what is not its case. */
final class PrecisionChecks {
  private static final String MESSAGE = "message";

  private PrecisionChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "transient-in-plain-class",
            toml -> new TransientInPlainClassCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "lazy-initialization",
            toml -> new LazyInitializationCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "int-limit-comparison",
            toml -> new IntLimitComparisonCheck(TomlValues.required(toml, MESSAGE))
        )
    );
  }
}
