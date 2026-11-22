package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiCompatGateRustTest {
  private static final String MANIFEST = "Cargo.toml";
  private static final String PACKAGE = "[package]\nname = \"demo\"\nversion = \"1.0.0\"\n";
  private static final String LIB = "src/lib.rs";
  private static final String BOTH = "pub fn open() {}\npub fn close() {}\n";
  private static final String ONLY_OPEN = "pub fn open() {}\n";

  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  private void crate(final String root, final String baseline, final String revision) {
    final String prefix = root.isEmpty() ? "" : root + "/";
    fixtures.baseline(prefix + MANIFEST, PACKAGE);
    fixtures.working.put(prefix + MANIFEST, PACKAGE);
    fixtures.baseline(prefix + LIB, baseline);
    fixtures.working.put(prefix + LIB, revision);
  }

  @Test
  @DisplayName("a removed public member")
  void aRemovedPublicMember() {
    crate("", BOTH, ONLY_OPEN);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("api-compat:member-removed");
    assertThat(finding.message()).contains("crate.<top-level>#close");
    assertThat(finding.file()).isEqualTo(LIB);
  }

  @Test
  @DisplayName("an unchanged Rust API passes and a private removal is not a break")
  void unchangedPasses() {
    crate("", BOTH + "fn local() {}\n", BOTH);

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  @DisplayName("the crates of a workspace are compared, a glob member included")
  void workspaceMembers() {
    fixtures.baseline(MANIFEST, "[workspace]\nmembers = [\"crates/*\"]\n");
    fixtures.working.put(MANIFEST, "[workspace]\nmembers = [\"crates/*\"]\n");
    crate("crates/core", BOTH, ONLY_OPEN);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().message()).contains("core.<top-level>#close");
  }

  @Test
  @DisplayName("a Rust syntax error is an execution error")
  void syntaxError() {
    crate("", BOTH, "pub fn (");

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.EXECUTION_ERROR);
  }
}
