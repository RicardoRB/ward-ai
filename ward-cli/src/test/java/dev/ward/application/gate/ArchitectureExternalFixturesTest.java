package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The {@code external} selector of {@code ward-architecture.toml} through the {@code ward} gate: a
 * seeded import of a library fails the rule in every language, and the fixture alone passes.
 */
class ArchitectureExternalFixturesTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final int THIRD_LINE = 3;
  private static final String RULE =
      """
      version = 1

      [[rule]]
      name = "no-framework"
      select = { directories = ["%s"] }
      must-not-depend-on = { external = ["%s"] }
      because = "this layer must not know the framework"
      """;

  @TempDir Path root;

  /** One language: its fixture, the selected directory, the entry, and the seeded file. */
  record Seed(
      String fixture,
      String directory,
      String external,
      String file,
      String source,
      int line,
      String name
  ) {}

  static List<Seed> seeds() {
    return List.of(
        new Seed(
            "java-resolution",
            "domain",
            "org.springframework",
            "src/main/java/shop/domain/Seeded.java",
            "package shop.domain;\n\nimport org.springframework.stereotype.Service;\n\n"
                + "class Seeded {\n  Service service;\n}\n",
            THIRD_LINE,
            "org.springframework.stereotype.Service"
        ),
        new Seed(
            "kotlin-resolution",
            "legacy",
            "io.ktor",
            "domain/src/main/java/shop/legacy/Seeded.kt",
            "package shop.legacy\n\nimport io.ktor.server.application.Application\n\n"
                + "class Seeded(val application: Application)\n",
            THIRD_LINE,
            "io.ktor.server.application.Application"
        ),
        new Seed(
            "node-architecture/typescript",
            "domain",
            "express",
            "src/domain/seeded.ts",
            "import express from \"express\";\n\nexport const app = express();\n",
            1,
            "express"
        ),
        new Seed(
            "python-resolution",
            "controller",
            "flask",
            "src/shop/controller/seeded.py",
            "from flask import Flask\n\napp = Flask(__name__)\n",
            1,
            "flask.Flask"
        ),
        new Seed(
            "swift-resolution",
            "Shop",
            "UIKit",
            "Modules/Shop/Seeded.swift",
            "import UIKit\n\nstruct Seeded {}\n",
            1,
            "UIKit"
        )
    );
  }

  @ParameterizedTest
  @MethodSource("seeds")
  void aSeededImportOfExternalCodeFailsTheRule(final Seed seed) throws IOException {
    copy(seed);
    final Path file = root.resolve(seed.file());
    Files.createDirectories(file.getParent());
    Files.writeString(file, seed.source());

    final GateResult result = ArchitectureGateRun.run(root);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items()).hasSize(1);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("ward:architecture/dependencies");
    assertThat(finding.file()).isEqualTo(seed.file());
    assertThat(finding.line()).isEqualTo(seed.line());
    assertThat(finding.message())
        .isEqualTo(
            "no-framework: "
                + seed.file()
                + " must not depend on "
                + seed.name()
                + ", because this layer must not know the framework"
        );
  }

  @ParameterizedTest
  @MethodSource("seeds")
  void theFixtureWithoutTheImportPasses(final Seed seed) throws IOException {
    copy(seed);

    assertThat(ArchitectureGateRun.run(root).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void anImportThatResolvesToAProjectFileIsNotExternal() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);
    Files.writeString(root.resolve("ward-architecture.toml"), RULE.formatted("legacy", "shop"));

    assertThat(ArchitectureGateRun.run(root).status()).isEqualTo(GateStatus.PASSED);
  }

  private void copy(final Seed seed) throws IOException {
    final Path base = FIXTURES.resolve(seed.fixture());
    ArchitectureGateRun.copy(base, root, file -> !file.startsWith(base.resolve("violations")));
    Files.writeString(
        root.resolve("ward-architecture.toml"),
        RULE.formatted(seed.directory(), seed.external())
    );
  }
}
