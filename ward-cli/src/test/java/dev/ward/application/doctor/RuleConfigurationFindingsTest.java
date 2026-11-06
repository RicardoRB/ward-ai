package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleConfigurationFindingsTest {

  @TempDir Path root;

  @Test
  void aProjectWithoutTheFileHasNoFinding() throws IOException {
    assertThat(diagnose()).isEmpty();
  }

  @Test
  void aValidFileIsOk() throws IOException {
    Files.writeString(root.resolve("ward-rules.toml"), "version = 1\n");

    assertThat(diagnose())
        .singleElement()
        .satisfies(f -> assertThat(f.status()).isEqualTo(Status.OK));
  }

  @Test
  void eachProblemIsAFailingFinding() throws IOException {
    Files.writeString(
        root.resolve("ward-rules.toml"),
        "version = 1\n[rules.\"ward:java/method-length\"]\nmax = 0\n[rules.\"ward:java/zzz\"]\n");

    assertThat(diagnose())
        .hasSize(2)
        .allSatisfy(f -> assertThat(f.status()).isEqualTo(Status.ERROR))
        .anyMatch(
            f -> f.message().contains("not selected") || f.message().contains("max: expected"))
        .anyMatch(f -> f.message().contains("unknown rule id"));
  }

  private List<Finding> diagnose() throws IOException {
    Files.writeString(root.resolve("ward.toml"), "version = 2\n[checks.ward]\nuses = \"ward\"\n");
    final List<Finding> findings = new ArrayList<>();
    RuleConfigurationFindings.diagnose(
        new TomlConfigurationReader(),
        new ProjectRuleSource(new ClasspathRuleSource()),
        root,
        findings);
    return findings;
  }
}
