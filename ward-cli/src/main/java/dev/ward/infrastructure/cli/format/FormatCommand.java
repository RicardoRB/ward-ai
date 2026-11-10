package dev.ward.infrastructure.cli.format;

import dev.ward.application.format.FormatFileOutcome;
import dev.ward.application.format.FormatFileOutcome.Verdict;
import dev.ward.application.format.FormatService;
import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward format} command: formats the project's source files in Ward style. */
@Command(
    name = "format",
    description = "Format the project's source files in Ward style.",
    mixinStandardHelpOptions = true,
    footer = {
      "",
      "Exit codes: 0 = formatted or already formatted, 1 = a file needs formatting (--check),"
          + " 2 = error or a file was refused"
    })
public final class FormatCommand implements Callable<Integer> {
  private static final Logger LOGGER = Logger.getLogger(FormatCommand.class.getName());
  private static final String TEXT = "text";
  private static final String JSON = "json";

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(names = "--check", description = "Write nothing; exit 1 when a file needs formatting.")
  private boolean check;

  @Option(
      names = "--format",
      paramLabel = "<format>",
      defaultValue = TEXT,
      description = "Output format: text or json (default: ${DEFAULT-VALUE}).")
  private String format;

  @Parameters(
      arity = "0..*",
      paramLabel = "<path>",
      description = "Files or folders to format (default: the whole project).")
  private final List<Path> paths = new ArrayList<>();

  private final FormatService service;
  private final JsonCodec codec;

  /** Creates the command over the formatting use case and the JSON codec. */
  @Inject
  public FormatCommand(final FormatService service, final JsonCodec codec) {
    this.service = service;
    this.codec = codec;
  }

  @Override
  public Integer call() {
    if (!TEXT.equals(format) && !JSON.equals(format)) {
      error().println("ward format: --format must be text or json");
      return ExitCodes.ERROR;
    }
    try {
      final List<FormatFileOutcome> outcomes = service.format(options.directory(), paths, !check);
      LOGGER.fine(() -> "event=format-command check=" + check + " files=" + outcomes.size());
      return render(outcomes);
    } catch (final WardException exception) {
      return fail(exception.getMessage());
    }
  }

  private int render(final List<FormatFileOutcome> outcomes) {
    if (JSON.equals(format)) {
      out().println(codec.toJson(new ReportDto(outcomes.stream().map(FileDto::of).toList(), null)));
    } else {
      outcomes.forEach(this::printText);
    }
    return exitCode(outcomes);
  }

  private void printText(final FormatFileOutcome outcome) {
    switch (outcome.verdict()) {
      case WRITTEN -> out().println("formatted " + outcome.path());
      case NEEDS_FORMATTING ->
          out().println("needs formatting " + outcome.path() + ":" + outcome.line());
      case REFUSED ->
          error()
              .println(
                  "refused " + outcome.path() + ":" + outcome.line() + ": " + outcome.reason());
      case FAILED -> error().println("failed " + outcome.path() + ": " + outcome.reason());
      default -> {
        // A file that is already formatted needs no report.
      }
    }
  }

  /** A refused or unreadable file is an error; with {@code --check}, a file to format is exit 1. */
  private int exitCode(final List<FormatFileOutcome> outcomes) {
    if (outcomes.stream().anyMatch(outcome -> broken(outcome.verdict()))) {
      return ExitCodes.ERROR;
    }
    if (check
        && outcomes.stream().anyMatch(outcome -> outcome.verdict() == Verdict.NEEDS_FORMATTING)) {
      return ExitCodes.FAILED;
    }
    return ExitCodes.OK;
  }

  private static boolean broken(final Verdict verdict) {
    return verdict == Verdict.REFUSED || verdict == Verdict.FAILED;
  }

  private int fail(final String message) {
    if (JSON.equals(format)) {
      out().println(codec.toJson(new ReportDto(List.of(), message)));
    } else {
      error().println("ward format: " + message);
    }
    return ExitCodes.ERROR;
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

  /**
   * The JSON report: one entry per file, and the error that stopped the run, if any.
   *
   * @param files the files, sorted by path
   * @param error the message of the error that stopped the run, or null
   */
  @JsonType
  public record ReportDto(List<FileDto> files, String error) {
    /** Copies the files. */
    public ReportDto {
      files = List.copyOf(files);
    }
  }

  /**
   * One file of the JSON report.
   *
   * @param path the path relative to the project root
   * @param status the verdict, in lower case with dashes
   * @param line the first line that differs, or zero
   * @param reason the message for the user, or empty
   */
  @JsonType
  public record FileDto(String path, String status, int line, String reason) {
    static FileDto of(final FormatFileOutcome outcome) {
      return new FileDto(
          outcome.path(),
          outcome.verdict().name().toLowerCase(Locale.ROOT).replace('_', '-'),
          outcome.line(),
          outcome.reason());
    }
  }
}
