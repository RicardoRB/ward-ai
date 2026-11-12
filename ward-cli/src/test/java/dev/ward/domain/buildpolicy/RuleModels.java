package dev.ward.domain.buildpolicy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds models and runs a rule over them, for the rule tests. */
final class RuleModels {
  static final int LINE = 3;

  private RuleModels() {}

  static Declaration declaration(
      final BuildEcosystem ecosystem,
      final DeclarationKind kind,
      final String version,
      final Declaration.Flag flag) {
    return new Declaration(ecosystem, kind, "x", version, "s", "x", "f", LINE, flag);
  }

  static List<BuildPolicyFinding> run(final BuildRule rule, final BuildModel model) {
    return BuildPolicyRules.forRule(rule)
        .check(new BuildPolicyInput(model, RuleModels::emptyInventory, Map.of()));
  }

  static DependencyInventory emptyInventory() {
    return new DependencyInventory(List.of());
  }

  static BuildModel declarations(final Declaration... declarations) {
    return new BuildModel(List.of(declarations), List.of());
  }

  static Dependency entry(final String name, final String version) {
    return new Dependency(
        new PackageId(Ecosystem.NPM, name, version),
        DependencyRelation.DIRECT,
        DependencyScope.RUNTIME,
        true,
        "package-lock.json",
        "package.json",
        Set.of(),
        Set.of());
  }
}
