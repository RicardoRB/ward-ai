package dev.ward.domain.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyInventoryTest {
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
            .direct(react, DependencyScope.RUNTIME, PACKAGE_JSON)
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
