package dev.ward.infrastructure.cli.sbom;

import dev.ward.application.license.Document;
import dev.ward.application.license.DocumentOutput;
import dev.ward.application.license.SbomService;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import dev.ward.infrastructure.cli.licenses.DocumentOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The {@code ward sbom} command: writes the CycloneDX 1.6 JSON bill of materials. */
@Command(
    name = "sbom",
    description = "Write the CycloneDX 1.6 JSON software bill of materials of the project.",
    mixinStandardHelpOptions = true,
    footer = {
      "",
      "Exit codes: 0 = written, 2 = error (for example an unusable dependency inventory)"
    })
public final class SbomCommand implements Callable<Integer> {
  private static final Logger LOGGER = Logger.getLogger(SbomCommand.class.getName());

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Mixin private final DocumentOptions document = new DocumentOptions();

  private final SbomService service;
  private final DocumentOutput output;

  /** Creates the command over the SBOM use case and the output port. */
  @Inject
  public SbomCommand(final SbomService service, final DocumentOutput output) {
    this.service = service;
    this.output = output;
  }

  @Override
  public Integer call() {
    try {
      final Document sbom = service.generate(options.directory(), document.scopes());
      sbom.warnings().forEach(warning -> error().println(warning));
      LOGGER.fine(() -> "event=sbom-command warnings=" + sbom.warnings().size());
      if (document.output() == null) {
        out().print(sbom.content());
        out().flush();
      } else {
        output.save(document.output(), sbom);
      }
      return ExitCodes.OK;
    } catch (final WardException exception) {
      error().println("ward sbom: " + exception.getMessage());
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
