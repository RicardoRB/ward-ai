package dev.ward.application.gate;

/** Port that loads the declarative analysis rules the {@code ward} gate can select. */
@FunctionalInterface
public interface AnalysisRuleSource {

  /** Loads every rule file. A file that cannot be used is reported and contributes no rules. */
  AnalysisRuleLoad load();
}
