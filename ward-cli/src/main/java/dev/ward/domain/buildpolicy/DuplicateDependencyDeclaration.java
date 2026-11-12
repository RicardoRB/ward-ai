package dev.ward.domain.buildpolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code ward:build/duplicate-dependency-declaration}: a POM declares a dependency once. */
final class DuplicateDependencyDeclaration implements BuildPolicyRule {
  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    final Map<String, Declaration> first = new HashMap<>();
    final List<BuildPolicyFinding> findings = new ArrayList<>();
    for (final Declaration declaration : input.model().declarations()) {
      if (declaration.ecosystem() != BuildEcosystem.MAVEN
          || declaration.kind() != DeclarationKind.DEPENDENCY) {
        continue;
      }
      final String key =
          declaration.file() + "\n" + declaration.section() + "\n" + declaration.identity();
      final Declaration earlier = first.putIfAbsent(key, declaration);
      if (earlier != null) {
        findings.add(finding(declaration, earlier));
      }
    }
    return findings;
  }

  private static BuildPolicyFinding finding(final Declaration repeat, final Declaration earlier) {
    return new BuildPolicyFinding(
        BuildRule.DUPLICATE_DEPENDENCY_DECLARATION,
        repeat.name()
            + " is declared more than once in "
            + repeat.section()
            + "; the first declaration is at line "
            + earlier.line()
            + ".",
        repeat.file(),
        repeat.line());
  }
}
