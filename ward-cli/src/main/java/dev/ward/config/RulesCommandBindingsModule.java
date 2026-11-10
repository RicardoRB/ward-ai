package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.ward.infrastructure.cli.rules.RulesCommand;
import dev.ward.infrastructure.cli.rules.RulesDisableCommand;
import dev.ward.infrastructure.cli.rules.RulesEnableCommand;
import dev.ward.infrastructure.cli.rules.RulesListCommand;
import dev.ward.infrastructure.cli.rules.RulesShowCommand;

/** Command bindings for the {@code ward rules} command group. */
@Module
interface RulesCommandBindingsModule {

  @Binds
  @IntoMap
  @ClassKey(RulesCommand.class)
  Object rulesCommand(RulesCommand command);

  @Binds
  @IntoMap
  @ClassKey(RulesListCommand.class)
  Object rulesListCommand(RulesListCommand command);

  @Binds
  @IntoMap
  @ClassKey(RulesShowCommand.class)
  Object rulesShowCommand(RulesShowCommand command);

  @Binds
  @IntoMap
  @ClassKey(RulesEnableCommand.class)
  Object rulesEnableCommand(RulesEnableCommand command);

  @Binds
  @IntoMap
  @ClassKey(RulesDisableCommand.class)
  Object rulesDisableCommand(RulesDisableCommand command);
}
