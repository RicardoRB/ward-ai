package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.swift.SwiftTarget;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PackageManifestTargetsTest {

  @Test
  void readsLiteralTargetsWithDefaultAndCustomPaths() {
    final Optional<List<SwiftTarget>> targets =
        PackageManifestTargets.read(
            """
            // swift-tools-version: 6.0
            let package = Package(
                name: "Shop",
                products: [.library(name: "Shop", targets: ["Shop"])],
                targets: [
                    .target(name: "Domain"),
                    .executableTarget(name: "App", dependencies: [.product(name: "Logger", package: "log")], path: "./Sources/App/"),
                    .testTarget(name: "ShopTests", dependencies: ["Shop", .target(name: "Domain")]),
                ]
            )
            """);

    assertThat(targets).isPresent();
    assertThat(targets.get().stream().map(SwiftTarget::name))
        .containsExactly("Domain", "App", "ShopTests");
    assertThat(targets.get().get(0).directories()).containsExactly("Sources/Domain");
    assertThat(targets.get().get(1).directories()).containsExactly("Sources/App");
    assertThat(targets.get().get(1).dependencies()).isEmpty();
    assertThat(targets.get().get(2).directories()).containsExactly("Tests/ShopTests");
    assertThat(targets.get().get(2).dependencies()).containsExactly("Shop", "Domain");
  }

  @Test
  void aComputedTargetListIsUnreadable() {
    assertThat(
            PackageManifestTargets.read(
                """
                let names = ["A", "B"]
                let package = Package(name: "X", targets: names.map { .target(name: $0) })
                """))
        .isEmpty();
  }

  @Test
  void aTargetWithoutALiteralNameIsUnreadable() {
    assertThat(
            PackageManifestTargets.read(
                "let package = Package(name: \"X\", targets: [.target(name: target)])"))
        .isEmpty();
  }
}
