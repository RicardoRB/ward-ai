package dev.ward.infrastructure.cli.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.GateToolInstallCatalog;
import dev.ward.domain.init.OpenSpecDistribution;
import dev.ward.domain.init.ScriptReplacement;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The yes/no questions init asks: overwrite, installation consent, hooks, review, updates. */
final class InitConfirmations {
  private final LinePrompt prompt;
  private final PrintWriter out;
  private final Answering answering;

  InitConfirmations(final LinePrompt prompt, final PrintWriter out, final boolean terminal) {
    this.prompt = prompt;
    this.out = out;
    this.answering = new Answering(terminal, out);
  }

  boolean overwrite(final Path file) {
    out.print(file + " already exists. Overwrite it? [y/N]: ");
    out.flush();
    return prompt.confirm("overwrite");
  }

  boolean openSpecInstallation(final Path destination) {
    out.println("OpenSpec is unavailable and can be installed locally.");
    out.println(
        "Package: "
            + OpenSpecDistribution.packageName()
            + " ("
            + OpenSpecDistribution.nodeRequirement()
            + ")");
    out.println("Destination: " + destination);
    out.println("This may access the network and run npm lifecycle scripts.");
    out.print("Install OpenSpec? [y/N]: ");
    out.flush();
    return prompt.confirmInstallation();
  }

  boolean gateToolInstallation(
      final String tool, final GateToolInstallCatalog.InstallMethod method) {
    out.println("The selected gate requires unavailable tool '" + tool + "'.");
    out.println("Install command: " + String.join(" ", method.arguments()));
    out.print("Install " + tool + "? [y/N]: ");
    out.flush();
    return prompt.confirmInstallation();
  }

  /** Asks to apply the reviewed plan; Enter means yes, and end of input means no. */
  boolean review() {
    return answering.ask(
        () -> {
          out.print("Proceed? [Y/n]: ");
          out.flush();
          final Optional<String> answer = prompt.readOrDefault("review");
          if (answer.isEmpty()) {
            return false;
          }
          return switch (answer.get().trim().toLowerCase(Locale.ROOT)) {
            case "", "y", "yes" -> true;
            case "n", "no" -> false;
            default -> throw new InvalidAnswerException("Answer y or n.");
          };
        });
  }

  boolean qualityHooks() {
    out.print("Install Ward pre-commit and pre-push quality hooks? [y/N]: ");
    out.flush();
    return prompt.confirm("quality-hooks");
  }

  /**
   * Lists each member script that already runs a different command, then asks once. Only {@code y}
   * replaces them; a {@code n} or the end of input keeps them.
   */
  boolean replaceScripts(final List<ScriptReplacement> replacements) {
    out.println("These member scripts already run a different command:");
    replacements.forEach(
        replacement ->
            out.println(
                "  "
                    + replacement.member()
                    + " "
                    + replacement.script()
                    + ": "
                    + replacement.current()
                    + " -> "
                    + replacement.proposed()));
    out.print("Replace them? [y/n]: ");
    out.flush();
    return prompt.confirm("replace-scripts");
  }

  boolean compatibilityUpdate(final CompatibilityFinding finding) {
    out.println(
        "Compatibility update proposed for "
            + finding.artifactId()
            + ": "
            + finding.currentVersion()
            + " -> "
            + finding.selectedVersion());
    out.println("Reason: " + finding.message());
    out.print("Apply this Maven plugin update? [y/N]: ");
    out.flush();
    return prompt.confirm("compatibility-update");
  }
}
