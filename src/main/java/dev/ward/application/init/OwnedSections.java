package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ward-owned sections init writes: the CI workflow and the agent rules a selection asks for,
 * and a Node project's {@code .gitignore} entries.
 */
final class OwnedSections {
  static final String GITIGNORE_BEGIN = "# ward-owned: gitignore v1 begin";
  static final String GITIGNORE_END = "# ward-owned: gitignore v1 end";

  private OwnedSections() {}

  /**
   * Returns the sections in the order init writes them. {@code userOwnedRuleFiles} are rule files
   * init keeps as the user wrote them.
   */
  static List<OwnedSection> of(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final List<Path> userOwnedRuleFiles) {
    final List<OwnedSection> sections = new ArrayList<>();
    if (selection.ci() != null) {
      sections.add(catalog.ciWorkflow(selection.ci(), project, gates));
    }
    if (project.toolchain() == Toolchain.NODE) {
      sections.add(
          new OwnedSection(
              project.root().resolve(".gitignore"),
              GITIGNORE_BEGIN,
              GITIGNORE_END,
              // Prettier skips Git-ignored files, so Vitest's coverage/ report is not checked.
              ".ward/reports/\ncoverage/\n",
              false));
    }
    if (selection.agentRules()) {
      sections.add(
          AgentRulesTemplate.render(project, gates, selection.preset(), userOwnedRuleFiles));
    }
    return sections;
  }
}
