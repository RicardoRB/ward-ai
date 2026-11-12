package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init offers {@code vulnerabilities} for every toolchain and enables it in no preset. */
class InitVulnerabilitiesOfferTest {
  @TempDir Path dir;

  private Project detect(final String file, final String content) throws IOException {
    Files.writeString(dir.resolve(file), content);
    return new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
  }

  private void assertOffered(final Project project) {
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    assertThat(catalog.gates(project, null))
        .extracting(InitGateOption::id)
        .contains(SupportedQualityGates.VULNERABILITIES);
    for (final QualityPreset preset : QualityPreset.values()) {
      assertThat(catalog.presetGates(project, preset))
          .doesNotContain(SupportedQualityGates.VULNERABILITIES);
    }
  }

  @Test
  void mavenProject() throws IOException {
    assertOffered(detect("pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>"));
  }

  @Test
  void nodeProject() throws IOException {
    Files.createDirectories(dir.resolve("src"));
    Files.writeString(dir.resolve("src/index.js"), "module.exports = 1;\n");
    assertOffered(detect("package.json", "{\"name\":\"demo\"}"));
  }

  @Test
  void pythonProject() throws IOException {
    Files.createDirectories(dir.resolve("src"));
    Files.writeString(dir.resolve("src/app.py"), "x = 1\n");
    assertOffered(detect("pyproject.toml", "[project]\nname = \"demo\"\nversion = \"1\"\n"));
  }

  @Test
  void swiftProject() throws IOException {
    Files.createDirectories(dir.resolve("Sources/Demo"));
    Files.writeString(dir.resolve("Sources/Demo/Demo.swift"), "let x = 1\n");
    assertOffered(
        detect(
            "Package.swift",
            "// swift-tools-version:5.9\nimport PackageDescription\n"
                + "let package = Package(name: \"Demo\")\n"));
  }
}
