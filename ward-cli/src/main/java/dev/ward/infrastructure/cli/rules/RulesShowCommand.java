package dev.ward.infrastructure.cli.rules;

import dev.ward.application.rules.RuleView;
import dev.ward.application.rules.RulesService;
import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.cli.ExitCodes;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward rules show} command: one rule with its options and presets. */
@Command(
    name = "show",
    description = "Show one analysis rule with its options and presets.",
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = shown, 2 = error (unknown rule id)"})
public final class RulesShowCommand implements Callable<Integer> {
  private static final String TEXT = "text";
  private static final String JSON = "json";

  @Spec private CommandSpec spec;

  @Parameters(
      index = "0",
      paramLabel = "<id>",
      description = "The rule id, such as ward:java/method-length.")
  private String id;

  @Option(
      names = "--format",
      paramLabel = "<format>",
      defaultValue = TEXT,
      description = "Output format: text or json (default: ${DEFAULT-VALUE}).")
  private String format;

  private final RulesService service;
  private final JsonCodec codec;

  /** Creates the command over the rules service and the JSON codec. */
  @Inject
  public RulesShowCommand(final RulesService service, final JsonCodec codec) {
    this.service = service;
    this.codec = codec;
  }

  @Override
  public Integer call() {
    if (!TEXT.equals(format) && !JSON.equals(format)) {
      RulesReport.error(spec).println("ward rules show: --format must be text or json");
      return ExitCodes.ERROR;
    }
    try {
      print(service.show(id));
      return ExitCodes.OK;
    } catch (final WardException exception) {
      RulesReport.error(spec).println("ward rules show: " + exception.getMessage());
      return ExitCodes.ERROR;
    }
  }

  private void print(final RuleView view) {
    if (JSON.equals(format)) {
      RulesReport.out(spec).println(codec.toJson(RulesReport.detail(view)));
    } else {
      RulesReport.details(view).forEach(line -> RulesReport.out(spec).println(line));
    }
  }
}
