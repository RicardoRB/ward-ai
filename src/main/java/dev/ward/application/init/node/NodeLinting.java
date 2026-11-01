package dev.ward.application.init.node;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.List;

/**
 * Which lint and format tools a Node project uses. Biome replaces Prettier and ESLint, and a
 * project's own ESLint config stays beside it.
 *
 * @param biome whether the project uses Biome
 * @param ownEslintConfig whether the project keeps its own ESLint config
 */
public record NodeLinting(boolean biome, boolean ownEslintConfig) {
  /** Detects the lint and format tools of {@code project} from its manifest and configs. */
  public static NodeLinting of(
      final Project project, final PackageManifest manifest, final NodeToolConfigs configs) {
    return new NodeLinting(
        NodeTools.biome(project, manifest, configs), NodeTools.ownEslintConfig(project, configs));
  }

  /** Returns the preset gate IDs for lint and format, in configuration order. */
  List<String> presetIds() {
    if (!biome) {
      return List.of("prettier", "eslint");
    }
    return ownEslintConfig ? List.of("biome", "eslint") : List.of("biome");
  }

  /**
   * Returns the lint and format gates in wizard order. {@code eslint} is the language's own ESLint
   * option, since the languages describe it differently.
   */
  List<InitGateOption> gates(final NodeTools node, final InitGateOption eslint) {
    if (biome) {
      return ownEslintConfig ? List.of(node.biomeGate(), eslint) : List.of(node.biomeGate());
    }
    return List.of(
        eslint,
        node.option(
            "prettier",
            "Checks formatting with Prettier.",
            node.command("prettier", "--check", ".")));
  }
}
