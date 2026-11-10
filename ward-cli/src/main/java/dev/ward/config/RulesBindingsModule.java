package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.application.rules.RuleBodyReader;
import dev.ward.domain.rules.RuleSelectionFile;
import dev.ward.infrastructure.rules.FileRuleSelectionFile;
import dev.ward.infrastructure.rules.TomlRuleBodyReader;

/** Binds the adapters of the {@code ward rules} command. */
@Module
interface RulesBindingsModule {

  @Binds
  RuleSelectionFile ruleSelectionFile(FileRuleSelectionFile implementation);

  @Binds
  RuleBodyReader ruleBodyReader(TomlRuleBodyReader implementation);
}
