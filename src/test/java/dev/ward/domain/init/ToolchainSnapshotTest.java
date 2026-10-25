package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.project.Framework;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolchainSnapshotTest {
  private static final int JAVA_11 = 11;
  private static final int JAVA_17 = 17;

  @Test
  void allowsAnUnknownRuntimeJdk() {
    final ToolchainSnapshot snapshot =
        new ToolchainSnapshot(Optional.empty(), Optional.empty(), Optional.empty(), null);

    assertThat(snapshot.runtimeJdk()).isEmpty();
    assertThat(snapshot.release()).isEmpty();
    assertThat(snapshot.framework()).isEqualTo(Framework.NONE);
  }

  @Test
  void releaseDefaultsToTheRuntimeJdk() {
    assertThat(
            new ToolchainSnapshot(
                    Optional.of(JAVA_17), Optional.empty(), Optional.of("3.9.9"), Framework.NONE)
                .release())
        .contains(JAVA_17);
    assertThat(
            new ToolchainSnapshot(
                    Optional.of(JAVA_17), Optional.of(JAVA_11), Optional.empty(), Framework.NONE)
                .release())
        .contains(JAVA_11);
  }

  @Test
  void rejectsANonPositiveVersion() {
    assertThatThrownBy(
            () ->
                new ToolchainSnapshot(
                    Optional.of(0), Optional.empty(), Optional.empty(), Framework.NONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ToolchainSnapshot(
                    Optional.of(JAVA_17), Optional.of(-1), Optional.empty(), Framework.NONE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
