package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.ArchitectureEngine;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Ward-owned sections init writes: the CI workflow and the agent rules a selection asks for,
 * and a Node project's {@code .gitignore} entries.
 */
public final class OwnedSections {
  static final String GITIGNORE_BEGIN = "# ward-owned: gitignore v1 begin";
  static final String GITIGNORE_END = "# ward-owned: gitignore v1 end";

  private OwnedSections() {}

  /**
   * Returns the sections in the order init writes them. {@code userOwnedRuleFiles} are rule files
   * init keeps as the user wrote them.
   */
  public static List<OwnedSection> of(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final List<Path> userOwnedRuleFiles) {
    final List<OwnedSection> sections =
        new ArrayList<>(ciSections(catalog, project, selection, gates));
    final String analysisCache = analysisCacheEntries(selection, gates);
    if (project.toolchain() == Toolchain.NODE) {
      sections.add(
          new OwnedSection(
              project.root().resolve(".gitignore"),
              GITIGNORE_BEGIN,
              GITIGNORE_END,
              // Ward's formatter skips Git-ignored files, so coverage and Stryker reports are not checked.
              ".ward/reports/\ncoverage/\n"
                  + project.taskRunner().gitignoreEntry()
                  + (gates.stream().anyMatch(gate -> "stryker".equals(gate.id()))
                      ? ".stryker-tmp/\nreports/mutation/\n"
                      : "")
                  + analysisCache,
              false));
    }
    if (project.toolchain() == Toolchain.PYTHON) {
      sections.add(
          new OwnedSection(
              project.root().resolve(".gitignore"),
              GITIGNORE_BEGIN,
              GITIGNORE_END,
              ".ward/reports/\nmutants/\n.coverage\n" + analysisCache,
              false));
    }
    InitToolchains.find(project)
        .flatMap(support -> support.gitignoreEntries(project, gates))
        .ifPresent(
            entries ->
                sections.add(
                    new OwnedSection(
                        project.root().resolve(".gitignore"),
                        GITIGNORE_BEGIN,
                        GITIGNORE_END,
                        entries + analysisCache,
                        false)));
    if (!analysisCache.isEmpty() && sections.stream().noneMatch(OwnedSections::isGitignore)) {
      sections.add(
          new OwnedSection(
              project.root().resolve(".gitignore"),
              GITIGNORE_BEGIN,
              GITIGNORE_END,
              analysisCache,
              false));
    }
    if (selection.agentRules()) {
      sections.add(
          AgentRulesTemplate.render(
              project,
              gates,
              selection.preset(),
              userOwnedRuleFiles,
              selection.architectureEngine() == ArchitectureEngine.WARD));
    }
    return sections;
  }

  /**
   * Returns {@code sections} with the project's {@code .editorconfig} appended when {@code
   * ward-format} writes it: {@code existing} is that file's content, empty when absent.
   */
  public static List<OwnedSection> withEditorConfig(
      final List<OwnedSection> sections,
      final Project project,
      final List<InitGateOption> gates,
      final Optional<String> existing) {
    final List<OwnedSection> all = new ArrayList<>(sections);
    EditorConfigTemplate.section(project, gates, existing).ifPresent(all::add);
    return List.copyOf(all);
  }

  /** Returns the project's {@code .editorconfig}, which the {@code ward} engine may write. */
  public static Path editorConfigPath(final Project project) {
    return EditorConfigTemplate.path(project);
  }

  private static List<OwnedSection> ciSections(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates) {
    if (selection.ci() == null) {
      return List.of();
    }
    return List.of(catalog.ciWorkflow(selection.ci(), project, gates));
  }

  /**
   * Returns the Git ignore entry for the analysis result cache, or nothing when the project has no
   * {@code ward} gate. The entry shares the one ignore section that init owns in the file.
   */
  private static String analysisCacheEntries(
      final InitSelection selection, final List<InitGateOption> gates) {
    final boolean wardEngine = selection.architectureEngine() == ArchitectureEngine.WARD;
    return wardEngine
            || gates.stream().anyMatch(gate -> SupportedQualityGates.ANALYSIS.equals(gate.id()))
        ? ".ward/cache/\n"
        : "";
  }

  private static boolean isGitignore(final OwnedSection section) {
    return GITIGNORE_BEGIN.equals(section.begin());
  }
}
