package dev.ward.infrastructure.cli.rules;

import dev.ward.application.rules.RuleFilter;
import dev.ward.application.rules.RuleView;
import dev.ward.application.rules.RulesService;
import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** The {@code ward rules list} command: the rules, filtered, as text or JSON. */
@Command(
    name = "list",
    description = "List analysis rules, with their presets and categories.",
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = listed, 2 = error (unknown filter or invalid configuration)"})
public final class RulesListCommand implements Callable<Integer> {
  private static final String TEXT = "text";
  private static final String JSON = "json";

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(
      names = "--preset",
      paramLabel = "<preset>",
      description = "Keep the rules that the preset selects: none, standard or strict.")
  private String preset;

  @Option(
      names = "--language",
      paramLabel = "<language>",
      description = "Keep the rules that cover this language.")
  private String language;

  @Option(
      names = "--category",
      paramLabel = "<category>",
      description = "Keep the rules of this category.")
  private String category;

  @Option(names = "--selected", description = "Keep only the rules that ward-rules.toml selects.")
  private boolean selected;

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
  public RulesListCommand(final RulesService service, final JsonCodec codec) {
    this.service = service;
    this.codec = codec;
  }

  @Override
  public Integer call() {
    if (!TEXT.equals(format) && !JSON.equals(format)) {
      RulesReport.error(spec).println("ward rules list: --format must be text or json");
      return ExitCodes.ERROR;
    }
    try {
      final RuleFilter filter = RuleFilter.parse(preset, language, category, selected);
      print(service.list(options.directory(), filter));
      return ExitCodes.OK;
    } catch (final WardException exception) {
      RulesReport.error(spec).println("ward rules list: " + exception.getMessage());
      return ExitCodes.ERROR;
    }
  }

  private void print(final List<RuleView> views) {
    if (JSON.equals(format)) {
      RulesReport.out(spec)
          .println(
              codec.toJson(
                  new RulesReport.RuleListDto(views.stream().map(RulesReport::rule).toList())));
    } else {
      views.forEach(view -> RulesReport.out(spec).println(RulesReport.line(view)));
    }
  }
}
