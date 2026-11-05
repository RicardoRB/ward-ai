package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The probe check the edit benchmark adds to a working copy, and the path it edits. */
class WardProbeTest {
  @TempDir Path copy;

  @Test
  void installAppendsAWardOnlyProbeCheckAfterTheExistingConfiguration() throws Exception {
    Files.writeString(copy.resolve("ward.toml"), "version = 2\n");

    WardProbe.install(copy);

    assertThat(Files.readString(copy.resolve("ward.toml")))
        .startsWith("version = 2\n")
        .contains(
            "[checks.ward-gate-edit]\nuses = \"ward\"\nprofiles = [\"ward-gate-edit\"]\n",
            "[checks.ward-gate-edit.paths]\n",
            "full = [\"ward.toml\"]\n");
  }

  @Test
  void editedPlaceholderIsReplacedByTheEditedPath() {
    final GateJob job =
        new GateJob(
            "ward-gate-edit",
            List.of(GateJob.WARD, "check", "--profile", WardProbe.CHECK, "--path", GateJob.EDITED),
            true,
            true);

    assertThat(job.executable(Path.of("/bin/ward"), "src/Alpha.java"))
        .containsExactly(
            "/bin/ward", "check", "--profile", WardProbe.CHECK, "--path", "src/Alpha.java");
  }
}
