package dev.ward.application.gate.buildpolicy;

import dev.ward.application.buildpolicy.BuildPolicyService;
import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildFiles;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runs the build policy gate over a fake build model, for the gate tests. */
final class GateFixtures {
  static final Project PROJECT =
      new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  static final int DYNAMIC_LINE = 7;
  static final Declaration DYNAMIC =
      new Declaration(
          BuildEcosystem.MAVEN,
          DeclarationKind.DEPENDENCY,
          "g:a",
          "[1,2)",
          "dependencies",
          "g:a",
          "pom.xml",
          DYNAMIC_LINE,
          Declaration.Flag.NONE);

  private GateFixtures() {}

  static RuleConfigFile.Select select(final String preset, final String... include) {
    return new RuleConfigFile.Select(preset, List.of(), List.of(), List.of(include), List.of());
  }

  static GateResult run(final BuildFiles files, final RuleConfigFile config) {
    final AnalysisRuleSource source =
        new AnalysisRuleSource() {
          @Override
          public AnalysisRuleLoad load() {
            return null;
          }

          @Override
          public Optional<RuleConfigFile> configuration(final Path root, final String file) {
            return Optional.ofNullable(config);
          }
        };
    final BuildPolicyService service =
        new BuildPolicyService(files, new DependencyInventoryService(Map.of()));
    final QualityGate gate = new BuildPolicyGates(service, source).create("policy", null);
    return gate.execute(PROJECT);
  }

  static BuildFiles model() {
    return root -> new BuildModel(List.of(DYNAMIC), List.of());
  }

  static RuleConfigFile file(
      final RuleConfigFile.Select select, final Map<String, Map<String, Object>> rules) {
    return new RuleConfigFile("ward-rules.toml", select, rules, List.of());
  }
}
