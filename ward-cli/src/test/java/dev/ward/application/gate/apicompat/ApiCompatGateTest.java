package dev.ward.application.gate.apicompat;

import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT;
import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT_PATH;
import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT_WITHOUT_CLOSE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiCompatGateTest {
  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  @Test
  void unchangedApiPassesAndNamesBaselineAndCount() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, CLIENT);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("v1.0.0").contains("1 API types");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void removedMethodFailsWithAFindingAtTheEnclosingType() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, CLIENT_WITHOUT_CLOSE);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("api-compat:member-removed");
    assertThat(finding.message()).contains("p.Client#close").endsWith("removed");
    assertThat(finding.file()).isEqualTo(CLIENT_PATH);
    assertThat(finding.line()).isEqualTo(2);
  }

  @Test
  void uncommittedNewSourcesAreTheNewApi() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, CLIENT);
    fixtures.working.put("src/main/java/p/Added.java", "package p;\npublic class Added {}\n");

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void testSourcesAreNotApi() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.baseline("src/test/java/p/Helper.java", "package p;\npublic class Helper {}\n");
    fixtures.working.put(CLIENT_PATH, CLIENT);

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void includeLimitsTheApi() {
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.baseline("src/main/java/q/Internal.java", "package q;\npublic class Internal {}\n");
    fixtures.working.put(CLIENT_PATH, CLIENT);

    assertThat(fixtures.run(new ApiCompatOptions(null, List.of("p"))).status())
        .isEqualTo(GateStatus.PASSED);
    assertThat(fixtures.run().status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void customSourceDirectoryAtTheBaselineIsRead() {
    final String pom = "<project><build><sourceDirectory>lib</sourceDirectory></build></project>";
    fixtures.baseline("pom.xml", pom);
    fixtures.baseline("lib/p/Client.java", CLIENT);
    fixtures.working.put("pom.xml", pom);
    fixtures.working.put("lib/p/Client.java", CLIENT_WITHOUT_CLOSE);

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.FAILED);
  }
}
