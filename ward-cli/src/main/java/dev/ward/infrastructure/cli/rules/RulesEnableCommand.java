package dev.ward.infrastructure.cli.rules;

import dev.ward.application.rules.RulesService;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward rules enable} command: edits {@code [select]} in {@code ward-rules.toml}. */
@Command(
    name = "enable",
    description = "Enable analysis rules in ward-rules.toml (added to [select] include).",
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = written, 2 = error (unknown rule id or invalid file)"})
public final class RulesEnableCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Parameters(arity = "1..*", paramLabel = "<id>", description = "The rule ids to enable.")
  private List<String> ids;

  private final RulesService service;

  /** Creates the command over the rules service. */
  @Inject
  public RulesEnableCommand(final RulesService service) {
    this.service = service;
  }

  @Override
  public Integer call() {
    try {
      service.enable(options.directory(), ids);
      RulesReport.out(spec).println("enabled " + String.join(", ", ids) + " in ward-rules.toml");
      return ExitCodes.OK;
    } catch (final WardException exception) {
      RulesReport.error(spec).println("ward rules enable: " + exception.getMessage());
      return ExitCodes.ERROR;
    }
  }
}
