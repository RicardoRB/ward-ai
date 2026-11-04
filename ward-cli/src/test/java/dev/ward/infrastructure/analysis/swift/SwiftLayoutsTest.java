package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.analysis.domain.swift.SwiftTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwiftLayoutsTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

  @TempDir Path root;

  @Test
  void swiftPackageTargetsOwnTheirDefaultDirectories() {
    final SwiftProjectLayout layout = SwiftLayouts.read(FIXTURES.resolve("swift-package"));

    assertThat(layout.moduleOf("Sources/Shop/Shop.swift")).isEqualTo("Shop");
    assertThat(layout.moduleOf("Tests/ShopTests/ShopTests.swift")).isEqualTo("ShopTests");
    assertThat(layout.targets()).extracting(SwiftTarget::name).containsExactly("Shop", "ShopTests");
  }

  @Test
  void swiftPackageCustomPathsAndDependenciesComeFromTheManifest() {
    final SwiftProjectLayout layout = SwiftLayouts.read(FIXTURES.resolve("swift-resolution"));

    assertThat(layout.moduleOf("Modules/Shop/Cart.swift")).isEqualTo("Shop");
    assertThat(layout.moduleOf("Sources/Domain/Order.swift")).isEqualTo("Domain");
    assertThat(layout.targets().get(1).dependencies()).containsExactly("Domain");
    assertThat(layout.targets().get(2).dependencies()).containsExactly("Shop");
  }

  @Test
  void xcodeTargetsOwnTheSourcesOfTheirBuildPhases() {
    final SwiftProjectLayout layout = SwiftLayouts.read(FIXTURES.resolve("swift-xcode-app"));

    assertThat(layout.moduleOf("ShopKit/Cart.swift")).isEqualTo("ShopKit");
    assertThat(layout.moduleOf("App/main.swift")).isEqualTo("Shop");
    assertThat(layout.moduleOf("ShopTests/ShopTests.swift")).isEqualTo("ShopTests");
    assertThat(layout.targets().get(2).dependencies()).containsExactly("ShopKit");
  }

  @Test
  void xcodeProjectWithTwoSchemesHasItsNativeTargets() {
    final SwiftProjectLayout layout =
        SwiftLayouts.read(FIXTURES.resolve("swift-xcode-two-schemes"));

    assertThat(layout.targets()).extracting(SwiftTarget::name).contains("ShopKit", "ShopTests");
    assertThat(layout.moduleOf("ShopKit/Cart.swift")).isEqualTo("ShopKit");
  }

  @Test
  void classicXcodeProjectResolvesGroupPathsThroughSourceRoot() {
    final SwiftProjectLayout layout = SwiftLayouts.read(FIXTURES.resolve("swift-xcode-classic"));

    assertThat(layout.moduleOf("Core/Cart.swift")).isEqualTo("ShopKit");
    assertThat(layout.moduleOf("ShopTests/ShopTests.swift")).isEqualTo("ShopTests");
    assertThat(layout.targets().get(2).dependencies()).containsExactly("ShopKit");
  }

  @Test
  void aComputedManifestFallsBackToTheImplicitModule() throws IOException {
    Files.writeString(
        root.resolve("Package.swift"),
        "let package = Package(name: \"X\", targets: names.map { .target(name: $0) })");
    Files.createDirectories(root.resolve("Sources/X"));

    final SwiftProjectLayout layout = SwiftLayouts.read(root);

    assertThat(layout.targets()).isEmpty();
    assertThat(layout.moduleOf("Sources/X/X.swift")).isEqualTo(SwiftProjectLayout.IMPLICIT_MODULE);
  }

  @Test
  void aMalformedXcodeProjectFallsBackToTheImplicitModule() throws IOException {
    final Path project = Files.createDirectories(root.resolve("Shop.xcodeproj"));
    Files.writeString(project.resolve("project.pbxproj"), "{ objects = { id = {");

    assertThat(SwiftLayouts.read(root).targets()).isEmpty();
  }

  @Test
  void isSwiftProjectRecognisesAManifestOrAProjectFile() throws IOException {
    assertThat(SwiftLayouts.isSwiftProject(root)).isFalse();
    Files.writeString(root.resolve("Package.swift"), "let package = Package(name: \"X\")");

    assertThat(SwiftLayouts.isSwiftProject(root)).isTrue();
  }
}
