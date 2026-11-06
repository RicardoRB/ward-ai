package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaTypeSidecarTest {
  @TempDir Path project;

  @Test
  void doesNotStartWithoutAJavaClasspathFile() {
    final JavaTypeSidecar sidecar = new JavaTypeSidecar(project, "test");

    assertThat(sidecar.start()).isEmpty();
    assertThat(sidecar.start(Optional.empty())).isEmpty();
  }

  @Test
  void doesNotStartWhenTheClasspathFileIsUnreadable() {
    assertThat(
            new JavaTypeSidecar(project, "test").start(Optional.of(project.resolve("missing.txt"))))
        .isEmpty();
  }
}
