package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The full CLI detects a Cargo crate and lets {@code init --yes} select its gates. */
@DisabledOnOs(OS.WINDOWS)
class WardCliRustProjectTest extends WardCliIntegrationSupport {
  @TempDir Path crate;

  @Test
  void detectAndInitSeeACargoCrate() throws IOException {
    Files.writeString(crate.resolve("Cargo.toml"), "[package]\nname = \"app\"\n");
    Files.createDirectories(crate.resolve("src"));
    Files.writeString(crate.resolve("src/lib.rs"), "pub fn one() -> u8 { 1 }\n");

    assertThat(run("detect", "-C", crate.toString())).isZero();
    assertThat(out.toString()).contains("Language:     Rust", "Build tool:   Cargo");

    assertThat(run(INIT, "--yes", INTEGRATION_OPTION, NONE, "-C", crate.toString()))
        .as(out + "\n" + err)
        .isZero();
    assertThat(Files.readString(crate.resolve(WARD_TOML)))
        .contains("[checks.cargo-clippy]", "[checks.cargo-test]");
  }
}
