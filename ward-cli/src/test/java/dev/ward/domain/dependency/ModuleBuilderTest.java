package dev.ward.domain.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleBuilderTest {
  private static final String LOCK = "package-lock.json";
  private static final String PACKAGE_JSON = "package.json";

  private static PackageId npm(final String name, final String version) {
    return new PackageId(Ecosystem.NPM, name, version);
  }

  private static Dependency entry(final ModuleDependencies module, final String name) {
    return module.dependencies().stream()
        .filter(dependency -> dependency.id().name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void directWinsOverTransitive() {
    final PackageId minimatch = npm("minimatch", "9.0.5");
    final PackageId eslint = npm("eslint", "9.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(eslint, DependencyScope.DEVELOPMENT, PACKAGE_JSON)
            .direct(minimatch, DependencyScope.RUNTIME, PACKAGE_JSON)
            .edge(eslint, minimatch)
            .build("");

    assertThat(entry(module, "minimatch").relation()).isEqualTo(DependencyRelation.DIRECT);
    assertThat(entry(module, "minimatch").declaredIn()).isEqualTo(PACKAGE_JSON);
    assertThat(entry(module, "minimatch").requiredBy()).containsExactly(eslint);
  }

  @Test
  void transitiveTakesTheScopeOfItsOnlyRequirerAndHasNoDeclaringFile() {
    final PackageId minimatch = npm("minimatch", "9.0.5");
    final PackageId eslint = npm("eslint", "9.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(eslint, DependencyScope.DEVELOPMENT, PACKAGE_JSON)
            .edge(eslint, minimatch)
            .build("");

    assertThat(entry(module, "minimatch").scope()).isEqualTo(DependencyScope.DEVELOPMENT);
    assertThat(entry(module, "minimatch").declaredIn()).isEmpty();
  }

  @Test
  void widestScopeOfTwoPathsWins() {
    final PackageId shared = npm("ms", "2.1.3");
    final PackageId debug = npm("debug", "4.0.0");
    final PackageId jest = npm("jest", "29.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(jest, DependencyScope.DEVELOPMENT, PACKAGE_JSON)
            .direct(debug, DependencyScope.RUNTIME, PACKAGE_JSON)
            .edge(jest, shared)
            .edge(debug, shared)
            .build("");

    assertThat(entry(module, "ms").scope()).isEqualTo(DependencyScope.RUNTIME);
    assertThat(module.dependencies().stream().filter(d -> "ms".equals(d.id().name()))).hasSize(1);
  }

  @Test
  void scopeFlowsThroughADiamondAndACycle() {
    final PackageId root = npm("root", "1.0.0");
    final PackageId left = npm("left", "1.0.0");
    final PackageId right = npm("right", "1.0.0");
    final PackageId bottom = npm("bottom", "1.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(root, DependencyScope.TEST, PACKAGE_JSON)
            .edge(root, left)
            .edge(root, right)
            .edge(left, bottom)
            .edge(right, bottom)
            .edge(bottom, left)
            .build("");

    assertThat(module.dependencies()).allMatch(d -> d.scope() == DependencyScope.TEST);
  }

  @Test
  void scopeIsNotPropagatedWhenTheSourceMediatedItAlready() {
    final PackageId parent = new PackageId(Ecosystem.MAVEN, "a:a", "1");
    final PackageId child = new PackageId(Ecosystem.MAVEN, "b:b", "1");
    final ModuleDependencies module =
        new ModuleBuilder("pom.xml", false)
            .scope(parent, DependencyScope.RUNTIME)
            .scope(child, DependencyScope.PROVIDED)
            .edge(parent, child)
            .build("");

    assertThat(entry(module, "b:b").scope()).isEqualTo(DependencyScope.PROVIDED);
  }

  @Test
  void entriesAreOrderedAndReleasedNeedsEveryAddToAgree() {
    final PackageId b = npm("b", "1.0.0");
    final PackageId a = npm("a", "1.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .add(b, true)
            .add(b, false)
            .direct(b, DependencyScope.RUNTIME, PACKAGE_JSON)
            .direct(a, DependencyScope.RUNTIME, PACKAGE_JSON)
            .build("");

    assertThat(module.dependencies()).extracting(d -> d.id().name()).containsExactly("a", "b");
    assertThat(entry(module, "b").released()).isFalse();
    assertThat(entry(module, "a").released()).isTrue();
  }
}
