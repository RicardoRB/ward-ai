package dev.ward.infrastructure.agent;

import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The OpenCode files of one Ward project. A root project keeps the original names and contents; a
 * nested project, installed at the repository root, gets files suffixed with its path slug whose
 * plugins run Ward from the project directory, found from the plugin's own location.
 *
 * @param projectPath the {@code /}-separated project path below the install root, or empty
 */
record OpenCodeFiles(String projectPath) {
  static final String COMMAND_FILE = ".opencode/commands/ward-check";
  static final String PLUGIN_FILE = ".opencode/plugins/ward-edit-write";
  private static final String PLUGINS_DIRECTORY = ".opencode/plugins";
  private static final String GUARD_NAME = "ward-[a-z]+-guard";
  private static final String GUARD_MARKER = "ward-owned: opencode-[a-z]+-guard v1";
  private static final String SESSION_CWD = "{ cwd: directory, ";
  private static final String PROJECT_CWD = "{ cwd: PROJECT, ";

  private boolean nested() {
    return !projectPath.isEmpty();
  }

  private String suffix() {
    return nested() ? "-" + Project.slug(projectPath) : "";
  }

  String commandFile() {
    return COMMAND_FILE + suffix() + ".md";
  }

  String pluginFile() {
    return PLUGIN_FILE + suffix() + ".js";
  }

  /** The guard plugins that earlier versions wrote for this project below {@code root}. */
  LegacyFiles legacyGuards(final Path root) {
    return new LegacyFiles(
        root.resolve(PLUGINS_DIRECTORY),
        Pattern.compile(GUARD_NAME + Pattern.quote(suffix() + ".js")),
        Pattern.compile(GUARD_MARKER));
  }

  String command(final String template) {
    return nested()
        ? template.replace("`ward check --format", "`ward check -C " + projectPath + " --format")
        : template;
  }

  /** Runs Ward from the project, with edited paths made absolute against the session directory. */
  String plugin(final String template) {
    if (!nested()) {
      return template;
    }
    return template
        .replace(
            "import { Plugin } from \"@opencode/plugin\";",
            "import { dirname, isAbsolute, join } from \"node:path\";\n"
                + "import { fileURLToPath } from \"node:url\";\n"
                + "import { Plugin } from \"@opencode/plugin\";")
        .replace("const EDIT_TOOLS", projectConstant() + "const EDIT_TOOLS")
        .replace(SESSION_CWD, PROJECT_CWD)
        .replace(
            "const paths = editedPaths(event.input, event.result);",
            "const paths = editedPaths(event.input, event.result)"
                + ".map((path) => (isAbsolute(path) ? path : join(directory, path)));")
        .replace(
            "id: \"ward.edit-write\"", "id: \"ward.edit-write." + Project.slug(projectPath) + "\"");
  }

  /** The project directory: two levels above the plugin file, then the project path. */
  private String projectConstant() {
    return "// Ward checks the project this repository-level plugin was installed for.\n"
        + "const PROJECT = join(dirname(fileURLToPath(import.meta.url)), \"..\", \"..\", "
        + jsString(projectPath)
        + ");\n";
  }

  private static String jsString(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
