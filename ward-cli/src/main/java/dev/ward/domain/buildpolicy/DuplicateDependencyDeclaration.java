package dev.ward.domain.buildpolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code ward:build/duplicate-dependency-declaration}: a POM or Cargo manifest declares a
 * dependency once. A Cargo finding names both lines.
 */
final class DuplicateDependencyDeclaration implements BuildPolicyRule {
  private static final Set<BuildEcosystem> CHECKED =
      Set.of(BuildEcosystem.MAVEN, BuildEcosystem.CARGO);

  @Override
  public List<BuildPolicyFinding> check(final BuildPolicyInput input) {
    final Map<String, Declaration> first = new HashMap<>();
    final List<BuildPolicyFinding> findings = new ArrayList<>();
    final Set<Declaration> reported = new HashSet<>();
    for (final Declaration declaration : input.model().declarations()) {
      if (!CHECKED.contains(declaration.ecosystem())
          || declaration.kind() != DeclarationKind.DEPENDENCY) {
        continue;
      }
      final String key =
          declaration.file() + "\n" + declaration.section() + "\n" + declaration.identity();
      final Declaration earlier = first.putIfAbsent(key, declaration);
      if (earlier != null) {
        if (declaration.ecosystem() == BuildEcosystem.CARGO && reported.add(earlier)) {
          findings.add(finding(earlier, earlier));
        }
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
            + (repeat == earlier
                ? "; it is repeated later."
                : "; the first declaration is at line " + earlier.line() + "."),
        repeat.file(),
        repeat.line()
    );
  }
}
