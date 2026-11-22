package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiCompatGateCSharpTest {
  private static final String PROJECT_FILE = "src/Lib/Lib.csproj";
  private static final String CLIENT_PATH = "src/Lib/Client.cs";
  private static final String PROJECT_TEXT = "<Project Sdk=\"Microsoft.NET.Sdk\" />";
  private static final String CLIENT =
      "namespace Lib;\npublic class Client {\n  public void Close() {}\n  internal void Hidden() {}\n}\n";
  private static final String CLIENT_WITHOUT_CLOSE =
      "namespace Lib;\npublic class Client {\n}\n";

  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  @Test
  @DisplayName("a removed public member")
  void aRemovedPublicMember() {
    fixtures.baseline(PROJECT_FILE, PROJECT_TEXT);
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(PROJECT_FILE, PROJECT_TEXT);
    fixtures.working.put(CLIENT_PATH, CLIENT_WITHOUT_CLOSE);

    final GateResult result = fixtures.run(ApiCompatGateFixtures.CSHARP_PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("api-compat:member-removed");
    assertThat(finding.message()).contains("Lib.Client#Close");
    assertThat(finding.file()).isEqualTo(CLIENT_PATH);
  }

  @Test
  void anUnchangedCSharpApiPassesAndAnInternalMemberIsNotApi() {
    fixtures.baseline(PROJECT_FILE, PROJECT_TEXT);
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(PROJECT_FILE, PROJECT_TEXT);
    fixtures.working.put(CLIENT_PATH, CLIENT.replace("Hidden", "Other"));

    final GateResult result = fixtures.run(ApiCompatGateFixtures.CSHARP_PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
  }
}
