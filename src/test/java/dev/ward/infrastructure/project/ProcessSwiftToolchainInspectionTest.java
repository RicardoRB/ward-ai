package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads the selected Xcode version from {@code xcodebuild -version} on macOS only. */
class ProcessSwiftToolchainInspectionTest {
  private static final String MAC_OS = "Mac OS X";

  @Test
  void readsTheXcodeVersionFromTheFirstLine() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(0, "Xcode 26.6\nBuild version 17E100\n", "");
    final ProcessSwiftToolchainInspection inspection =
        new ProcessSwiftToolchainInspection(commands, MAC_OS);

    assertThat(inspection.xcodeVersion()).hasValue("26.6");
    assertThat(inspection.macOs()).isTrue();
    assertThat(commands.commands).containsExactly(List.of("xcodebuild", "-version"));
  }

  @Test
  void failingXcodebuildGivesNoVersion() {
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(1, "", "xcode-select: error: tool 'xcodebuild' requires Xcode");

    assertThat(new ProcessSwiftToolchainInspection(commands, MAC_OS).xcodeVersion()).isEmpty();
  }

  @Test
  void otherSystemsAreNotMacOsAndRunNothing() {
    final FakeCommandExecutor commands = new FakeCommandExecutor(0, "Xcode 26.6\n", "");
    final ProcessSwiftToolchainInspection inspection =
        new ProcessSwiftToolchainInspection(commands, "Linux");

    assertThat(inspection.macOs()).isFalse();
    assertThat(inspection.xcodeVersion()).isEmpty();
    assertThat(commands.commands).isEmpty();
  }
}
