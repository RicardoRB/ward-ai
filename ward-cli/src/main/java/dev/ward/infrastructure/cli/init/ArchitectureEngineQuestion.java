package dev.ward.infrastructure.cli.init;

import dev.ward.domain.init.ArchitectureEngine;
import java.io.PrintWriter;
import java.util.Optional;

/** The wizard question that picks the checker of the architecture style. */
final class ArchitectureEngineQuestion {
  private static final String TOOL_CHOICE = "1";
  private static final String WARD_CHOICE = "2";

  private final Answering answering;
  private final LinePrompt prompt;
  private final PrintWriter out;

  ArchitectureEngineQuestion(
      final Answering answering, final LinePrompt prompt, final PrintWriter out) {
    this.answering = answering;
    this.prompt = prompt;
    this.out = out;
  }

  /** Asks which checker enforces the architecture style; an empty answer keeps the tool. */
  ArchitectureEngine select() {
    return answering.ask(
        () -> {
          out.println("Select which checks the architecture style:");
          out.println("> 1) tool - the generated external tool (default)");
          out.println("> 2) ward - the ward engine, with layer boundaries in ward.toml");
          out.print("Choose an engine [1-2, Enter=1]: ");
          out.flush();
          final Optional<String> answer = prompt.readOrDefault("architecture-engine");
          if (answer.isEmpty()
              || answer.get().isBlank()
              || TOOL_CHOICE.equals(answer.get().strip())) {
            return ArchitectureEngine.TOOL;
          }
          if (WARD_CHOICE.equals(answer.get().strip())) {
            return ArchitectureEngine.WARD;
          }
          throw new InvalidAnswerException(
              "Invalid architecture engine selection '" + answer.get() + "'. Choose 1 or 2.");
        });
  }
}
