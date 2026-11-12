package dev.ward.infrastructure.agent;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The Claude Code files and hook commands of one Ward project. A root project keeps the original
 * names and scripts; a nested project, installed at the repository root, gets files suffixed with
 * its path slug whose scripts point Ward at the project directory.
 *
 * @param projectPath the {@code /}-separated project path below the install root, or empty
 */
record ClaudeCodeFiles(String projectPath) {
  static final String HOOK_FILE = ".claude/hooks/ward-edit-write";
  private static final String HOOKS_DIRECTORY = ".claude/hooks";
  private static final String GUARD_NAME = "ward-[a-z]+-guard";
  private static final String GUARD_MARKER = "ward-owned: [a-z]+-guard v1";
  private static final String PROJECT_DIR = "${CLAUDE_PROJECT_DIR}/";
  private static final String SESSION_PROJECT = "-C \"${CLAUDE_PROJECT_DIR:-.}\"";

  /**
   * Validates the path.
   *
   * @throws WardException if the path cannot be written safely inside single quotes
   */
  ClaudeCodeFiles {
    if (projectPath.contains("'") || projectPath.contains("\n") || projectPath.contains("\r")) {
      throw new WardException(
          "Cannot install the Claude Code integration: the project path '"
              + projectPath.strip()
              + "' contains a quote or line break.");
    }
  }

  private String suffix() {
    return projectPath.isEmpty() ? "" : "-" + Project.slug(projectPath);
  }

  String hookFile() {
    return HOOK_FILE + suffix();
  }

  /** The hook commands this project registers, which are the only ones it may replace. */
  Set<String> commands() {
    if (projectPath.isEmpty()) {
      return Set.of(HOOK_FILE, PROJECT_DIR + HOOK_FILE);
    }
    return Set.of(PROJECT_DIR + hookFile());
  }

  /**
   * Accepts the commands this project may replace: its hook and the guard hooks that earlier
   * versions registered for it.
   */
  Predicate<String> owns() {
    final Set<String> current = commands();
    final Pattern guard =
        Pattern.compile(
            (projectPath.isEmpty() ? "(\\$\\{CLAUDE_PROJECT_DIR}/)?" : "\\$\\{CLAUDE_PROJECT_DIR}/")
                + Pattern.quote(HOOKS_DIRECTORY + "/")
                + GUARD_NAME
                + Pattern.quote(suffix()));
    return command -> current.contains(command) || guard.matcher(command).matches();
  }

  /** The guard scripts that earlier versions wrote for this project below {@code root}. */
  LegacyFiles legacyGuards(final Path root) {
    return new LegacyFiles(
        root.resolve(HOOKS_DIRECTORY),
        Pattern.compile(GUARD_NAME + Pattern.quote(suffix())),
        Pattern.compile(GUARD_MARKER));
  }

  /** Points a settings template at this project's hook files. */
  String settings(final String template) {
    return template.replace(PROJECT_DIR + HOOK_FILE + "\"", PROJECT_DIR + hookFile() + "\"");
  }

  /**
   * Points a hook script at this project. A nested project's script changes to the repository root
   * it lives under, two levels up, and passes the project path, so the session's start directory
   * does not matter.
   */
  String script(final String template) {
    if (projectPath.isEmpty()) {
      return template;
    }
    return template
        .replace("exec ward ", "cd \"$(dirname \"$0\")/../..\" || exit 2\nexec ward ")
        .replace(SESSION_PROJECT, "-C '" + projectPath + "'");
  }
}
