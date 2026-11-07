package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.RuleFileProvider;
import dev.ward.infrastructure.rule.BundledLanguages;
import java.util.Set;

/** Joins the rule files and the checks that each language module contributes. */
@Module
interface RuleBindingsModule {

  /** The rule file lists of the bundled language modules. */
  @Provides
  @ElementsIntoSet
  static Set<RuleFileProvider> ruleFiles() {
    return Set.copyOf(BundledLanguages.ruleFiles());
  }

  /** The check providers of the shared checks and of the bundled language modules. */
  @Provides
  @ElementsIntoSet
  static Set<CheckProvider> checkProviders() {
    return Set.copyOf(BundledLanguages.checkProviders());
  }
}
