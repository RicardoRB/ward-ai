package dev.ward.infrastructure.cli.init;

import dev.ward.domain.agent.SupportedAgents;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The {@code --prompt-hook} option of init: marks each selection and names unsupported agents. */
final class InitPromptHook {
  private InitPromptHook() {}

  /** Returns the targets with the prompt hook asked for or not in each selection. */
  static List<InitApply.Target> select(final List<InitApply.Target> targets, final boolean asked) {
    return targets.stream()
        .map(
            target ->
                new InitApply.Target(
                    target.project(),
                    target.selection().withPromptHook(asked),
                    target.overwrite()
                )
        )
        .toList();
  }

  /** Names the selected integrations that have no prompt event; they get no prompt hook. */
  static void report(
      final PrintWriter out,
      final List<InitApply.Target> targets,
      final boolean asked
  ) {
    if (!asked) {
      return;
    }
    final Set<String> ids = new LinkedHashSet<>();
    targets.forEach(target -> ids.addAll(target.selection().integrations()));
    final List<String> unsupported = SupportedAgents.withoutPromptHook(List.copyOf(ids));
    if (unsupported.size() < ids.stream().filter(SupportedAgents.ids()::contains).count()
        && targets.stream().anyMatch(target -> !target.selection().gates().contains("secrets"))) {
      out.println(
          "Prompt hook installed, but a project without a secrets gate scans no prompts:"
              + " add a secrets check and [checks.secrets.prompts] to ward.toml."
      );
    }
    unsupported
        .forEach(
            id ->
                out.printf(
                    "Prompt hook is not supported for %s: it has no hook event that can reject"
                        + " a prompt. None was installed.%n",
                    id
                )
        );
  }
}
