package dev.ward.infrastructure.cli.rules;

import dev.ward.infrastructure.cli.ExitCodes;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The {@code ward rules} command group; it runs only through a subcommand. */
@Command(
    name = "rules",
    description = "List, show, enable and disable analysis rules.",
    subcommands = {
      RulesListCommand.class,
      RulesShowCommand.class,
      RulesEnableCommand.class,
      RulesDisableCommand.class
    })
public final class RulesCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /** Creates the command group. */
  @Inject
  public RulesCommand() {
    // Container command; Dagger constructs it through this constructor.
  }

  @Override
  public Integer call() {
    if (spec != null) {
      spec.commandLine().usage(spec.commandLine().getErr());
    }
    return ExitCodes.ERROR;
  }
}
