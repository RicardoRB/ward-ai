package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whether an Angular project's angular.json sets the coverage line threshold ng-test enforces. */
class AngularCoverageThresholdsTest {
  @TempDir Path root;

  @Test
  void setLineThresholdInTheTestTargetIsDetected() throws IOException {
    Files.writeString(
        root.resolve("angular.json"),
        """
        {"projects": {"app": {"architect": {"test": {"options": {
          "coverageThresholds": {"lines": 80, "branches": 70}
        }}}}}}}
        """);

    assertThat(new FileSystemNodeToolConfigs().angularCoverageThresholdsSet(root)).isTrue();
  }

  @Test
  void angularJsonWithoutThresholdsIsNotDetected() throws IOException {
    Files.writeString(
        root.resolve("angular.json"),
        """
        {"projects": {"app": {"architect": {"test": {"options": {"coverage": true}}}}}}
        """);

    assertThat(new FileSystemNodeToolConfigs().angularCoverageThresholdsSet(root)).isFalse();
  }

  @Test
  void missingAngularJsonIsNotDetected() {
    assertThat(new FileSystemNodeToolConfigs().angularCoverageThresholdsSet(root)).isFalse();
  }
}
