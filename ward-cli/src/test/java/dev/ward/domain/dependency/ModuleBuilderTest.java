package dev.ward.domain.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleBuilderTest {
  private static final String LOCK = "package-lock.json";

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
            .direct(eslint, DependencyScope.DEVELOPMENT, "package.json")
            .direct(minimatch, DependencyScope.RUNTIME, "package.json")
            .edge(eslint, minimatch)
            .build("");

    assertThat(entry(module, "minimatch").relation()).isEqualTo(DependencyRelation.DIRECT);
    assertThat(entry(module, "minimatch").declaredIn()).isEqualTo("package.json");
    assertThat(entry(module, "minimatch").requiredBy()).containsExactly(eslint);
  }

  @Test
  void transitiveTakesTheScopeOfItsOnlyRequirerAndHasNoDeclaringFile() {
    final PackageId minimatch = npm("minimatch", "9.0.5");
    final PackageId eslint = npm("eslint", "9.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(eslint, DependencyScope.DEVELOPMENT, "package.json")
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
            .direct(jest, DependencyScope.DEVELOPMENT, "package.json")
            .direct(debug, DependencyScope.RUNTIME, "package.json")
            .edge(jest, shared)
            .edge(debug, shared)
            .build("");

    assertThat(entry(module, "ms").scope()).isEqualTo(DependencyScope.RUNTIME);
    assertThat(module.dependencies().stream().filter(d -> d.id().name().equals("ms"))).hasSize(1);
  }

  @Test
  void scopeFlowsThroughADiamondAndACycle() {
    final PackageId root = npm("root", "1.0.0");
    final PackageId left = npm("left", "1.0.0");
    final PackageId right = npm("right", "1.0.0");
    final PackageId bottom = npm("bottom", "1.0.0");
    final ModuleDependencies module =
        new ModuleBuilder(LOCK, true)
            .direct(root, DependencyScope.TEST, "package.json")
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
            .direct(b, DependencyScope.RUNTIME, "package.json")
            .direct(a, DependencyScope.RUNTIME, "package.json")
            .build("");

    assertThat(module.dependencies()).extracting(d -> d.id().name()).containsExactly("a", "b");
    assertThat(entry(module, "b").released()).isFalse();
    assertThat(entry(module, "a").released()).isTrue();
  }

  @Test
  void requestedVersionsAreKeptPerRequester() {
    final PackageId guava = new PackageId(Ecosystem.MAVEN, "g:guava", "33.0.0");
    final PackageId lib = new PackageId(Ecosystem.MAVEN, "x:lib", "1.0");
    final ModuleDependencies module =
        new ModuleBuilder("pom.xml", false)
            .scope(guava, DependencyScope.RUNTIME)
            .scope(lib, DependencyScope.RUNTIME)
            .requested(guava, RequestedVersion.byModule("33.0.0"))
            .requested(guava, RequestedVersion.by(lib, "30.0"))
            .build("");

    assertThat(entry(module, "g:guava").requested())
        .containsExactlyInAnyOrder(
            new RequestedVersion("", "33.0.0"),
            new RequestedVersion("pkg:maven/x/lib@1.0", "30.0"));
    assertThat(entry(module, "x:lib").requested()).isEmpty();
  }

  @Test
  void aPackageWithoutAnyScopeIsAnError() {
    final ModuleBuilder builder = new ModuleBuilder(LOCK, true).add(npm("a", "1.0.0"), true);

    assertThatThrownBy(() -> builder.build("")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void inventoryListsRootFirstAndDistinctPackagesAcrossModules() {
    final PackageId react = npm("react", "18.3.1");
    final ModuleDependencies web =
        new ModuleBuilder(LOCK, true)
            .direct(react, DependencyScope.RUNTIME, "web/package.json")
            .build("web");
    final ModuleDependencies root =
        new ModuleBuilder(LOCK, true)
            .direct(react, DependencyScope.RUNTIME, "package.json")
            .build("");
    final DependencyInventory inventory = new DependencyInventory(List.of(web, root));

    assertThat(inventory.modules())
        .extracting(ModuleDependencies::modulePath)
        .containsExactly("", "web");
    assertThat(inventory.distinct()).containsExactly(react);
    assertThat(inventory.of("web")).contains(web);
    assertThat(inventory.entryCount()).isEqualTo(2);
  }

  @Test
  void widestOrdersRuntimeProvidedTestDevelopment() {
    assertThat(DependencyScope.widest(DependencyScope.TEST, DependencyScope.PROVIDED))
        .isEqualTo(DependencyScope.PROVIDED);
    assertThat(DependencyScope.widest(DependencyScope.DEVELOPMENT, DependencyScope.RUNTIME))
        .isEqualTo(DependencyScope.RUNTIME);
  }
}
