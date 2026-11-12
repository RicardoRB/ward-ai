package dev.ward.infrastructure.cli.licenses;

import dev.ward.application.license.Document;
import dev.ward.application.license.DocumentOutput;
import dev.ward.application.license.NoticeService;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The {@code ward licenses} command: writes the third-party license notice. */
@Command(
    name = "licenses",
    description = "Write the third-party license notice of the project.",
    mixinStandardHelpOptions = true,
    footer = {
      "",
      "Exit codes: 0 = written, 2 = error (for example an unusable dependency inventory)"
    })
public final class LicensesCommand implements Callable<Integer> {
  private static final Logger LOGGER = Logger.getLogger(LicensesCommand.class.getName());

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Mixin private final DocumentOptions document = new DocumentOptions();

  private final NoticeService service;
  private final DocumentOutput output;

  /** Creates the command over the notice use case and the output port. */
  @Inject
  public LicensesCommand(final NoticeService service, final DocumentOutput output) {
    this.service = service;
    this.output = output;
  }

  @Override
  public Integer call() {
    try {
      final Document notice = service.generate(options.directory(), document.scopes());
      notice.warnings().forEach(warning -> error().println(warning));
      LOGGER.fine(() -> "event=licenses-command warnings=" + notice.warnings().size());
      if (document.output() == null) {
        out().print(notice.content());
        out().flush();
      } else {
        output.save(document.output(), notice);
      }
      return ExitCodes.OK;
    } catch (final WardException exception) {
      error().println("ward licenses: " + exception.getMessage());
      return ExitCodes.ERROR;
    }
  }

  private PrintWriter out() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }

  private PrintWriter error() {
    return spec == null
        ? new PrintWriter(System.err, true, StandardCharsets.UTF_8)
        : spec.commandLine().getErr();
  }
}
