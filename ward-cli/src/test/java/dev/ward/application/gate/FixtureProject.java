package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A temporary Java project and the {@code ward} gate over it, for the rule configuration tests. */
final class FixtureProject {
  static final String SRC_A = "src/A.java";
  static final String VERSION = "version = 1\n";
  private static final String RULES_FILE = "ward-rules.toml";

  private final Path root;
  private final ClasspathRuleSource packaged;

  FixtureProject(final Path root) {
    this(root, new ClasspathRuleSource());
  }

  FixtureProject(final Path root, final ClasspathRuleSource packaged) {
    this.root = root;
    this.packaged = packaged;
  }

  Path root() {
    return root;
  }

  void source(final String path, final String header, final int lines) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(
        file, header + " {\n  void run() {\n" + "    int a = 0;\n".repeat(lines) + "  }\n}\n");
  }

  void config(final String content) throws IOException {
    Files.writeString(root.resolve(RULES_FILE), content);
  }

  static List<String> ids(final GateResult result) {
    return result.findings().items().stream().map(Finding::ruleId).toList();
  }

  GateResult run(
      final List<String> rules, final String rulesFile, final Map<String, String> cache) {
    final GateConfiguration check =
        new GateConfiguration(
            "structure",
            "ward",
            true,
            List.of(),
            Set.of(),
            null,
            null,
            null,
            null,
            rules,
            null,
            List.of(),
            null,
            null,
            rulesFile,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ProjectRuleSource(packaged),
            new DeclarativeRuleMatcher(),
            new FileSystemSourceUnits(new ProcessCommandExecutor()),
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX))),
            unused -> AnalysisCacheFixtures.memory(cache),
            AnalysisCacheFixtures.codec(),
            "test",
            DependencyAdapters.none(),
            (unused, classpath) -> Map.of());
    return AnalysisGates.create(check, ports).execute(project());
  }

  private Project project() {
    return new Project(
        root,
        Language.JAVA,
        BuildTool.MAVEN,
        Framework.NONE,
        false,
        root,
        Set.of(),
        null,
        TaskRunner.NONE,
        null);
  }
}
