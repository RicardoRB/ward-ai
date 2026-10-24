package dev.ward.application.init;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;

/** The CI workflow steps that set up Node or Bun and install with a frozen lock file. */
final class NodeCiSetup {
  private static final String WITH = "        with:\n";

  private NodeCiSetup() {}

  /** Returns the setup and install steps for {@code project}'s package manager. */
  static String steps(final Project project, final String packageManager) {
    final BuildTool manager = project.buildTool();
    if (manager == BuildTool.BUN) {
      return CiWorkflowTemplate.STEP
          + "Set up Bun\n"
          + "        uses: oven-sh/setup-bun@v2\n"
          + install("bun install --frozen-lockfile");
    }
    final String pnpm =
        manager == BuildTool.PNPM
            ? CiWorkflowTemplate.STEP + "Set up pnpm\n" + "        uses: pnpm/action-setup@v4\n"
            : "";
    return pnpm
        + CiWorkflowTemplate.STEP
        + "Set up Node\n"
        + "        uses: actions/setup-node@v5\n"
        + WITH
        // Double quotes, as Prettier formats YAML.
        + "          node-version: \""
        + project.languageVersion()
        + "\"\n"
        + "          cache: "
        + project.buildExecutable()
        + "\n"
        + install(frozenInstall(manager, packageManager));
  }

  private static String frozenInstall(final BuildTool manager, final String packageManager) {
    return switch (manager) {
      case PNPM -> "pnpm install --frozen-lockfile";
      // Yarn 1 has no --immutable; it is the default when packageManager names no version.
      case YARN ->
          packageManager != null
                  && packageManager.startsWith("yarn@")
                  && !packageManager.startsWith("yarn@1.")
              ? "yarn install --immutable"
              : "yarn install --frozen-lockfile";
      default -> "npm ci";
    };
  }

  private static String install(final String command) {
    return CiWorkflowTemplate.STEP + "Install dependencies\n" + "        run: " + command + "\n";
  }
}
