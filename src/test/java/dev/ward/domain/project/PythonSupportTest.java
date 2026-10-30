package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PythonSupportTest {
  @ParameterizedTest
  @ValueSource(strings = {"2.7", "3.9", "3.10"})
  void olderPythonVersionIsIncompatible(final String version) {
    assertThat(PythonSupport.incompatibility(python(version)))
        .hasValueSatisfying(
            message ->
                assertThat(message)
                    .contains("Python " + version + " is INCOMPATIBLE")
                    .contains("3.11 to 3.14"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"3.11", "3.14", "3.15", "3.20"})
  void supportedPythonVersionIsNotIncompatible(final String version) {
    assertThat(PythonSupport.incompatibility(python(version))).isEmpty();
  }

  @Test
  void versionThatIsNotMajorMinorIsNotIncompatible() {
    assertThat(PythonSupport.incompatibility(python("latest"))).isEmpty();
  }

  @Test
  void projectWithoutVersionIsNotIncompatible() {
    assertThat(
            PythonSupport.incompatibility(
                new Project(Path.of("app"), Language.PYTHON, BuildTool.PIP, Framework.NONE)))
        .isEmpty();
  }

  private static Project python(final String version) {
    final Path root = Path.of("app");
    return new Project(
        root,
        Language.PYTHON,
        BuildTool.PIP,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        version,
        TaskRunner.NONE);
  }
}
