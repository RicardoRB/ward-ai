package dev.ward.analysis.application.rule;

import java.util.Collection;
import java.util.List;

/** Names the rule files that one language module packages as classpath resources. */
@FunctionalInterface
public interface RuleFileProvider {

  /** Returns the classpath resources of this module's rule files, sorted alphabetically. */
  List<String> ruleFiles();

  /** Returns the rule files of all {@code providers}, sorted alphabetically. */
  static List<String> sortedFiles(final Collection<? extends RuleFileProvider> providers) {
    return providers.stream().flatMap(provider -> provider.ruleFiles().stream()).sorted().toList();
  }
}
