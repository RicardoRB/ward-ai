package dev.ward.infrastructure.cli.init;

import dev.ward.domain.init.FormatEngine;
import java.io.PrintWriter;
import java.util.Optional;

/** The wizard question that picks the formatter that checks the source files. */
final class FormatEngineQuestion {
  private static final String TOOL_CHOICE = "1";
  private static final String WARD_CHOICE = "2";

  private final Answering answering;
  private final LinePrompt prompt;
  private final PrintWriter out;

  FormatEngineQuestion(final Answering answering, final LinePrompt prompt, final PrintWriter out) {
    this.answering = answering;
    this.prompt = prompt;
    this.out = out;
  }

  /** Asks which formatter checks the sources; an empty answer keeps the external tools. */
  FormatEngine select() {
    return answering.ask(
        () -> {
          out.println("Select which formatter checks the source files:");
          out.println("> 1) tool - the external formatters (default)");
          out.println("> 2) ward - the ward native formatter, with the layout in .editorconfig");
          out.print("Choose a formatter [1-2, Enter=1]: ");
          out.flush();
          final Optional<String> answer = prompt.readOrDefault("format-engine");
          if (answer.isEmpty()
              || answer.get().isBlank()
              || TOOL_CHOICE.equals(answer.get().strip())) {
            return FormatEngine.TOOL;
          }
          if (WARD_CHOICE.equals(answer.get().strip())) {
            return FormatEngine.WARD;
          }
          throw new InvalidAnswerException(
              "Invalid format engine selection '" + answer.get() + "'. Choose 1 or 2.");
        });
  }
}
