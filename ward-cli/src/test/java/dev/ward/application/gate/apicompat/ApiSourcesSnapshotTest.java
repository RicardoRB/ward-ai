package dev.ward.application.gate.apicompat;

import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT;
import static dev.ward.application.gate.apicompat.ApiCompatGateFixtures.CLIENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiSourcesSnapshotTest {
  @Test
  @DisplayName("the baseline is read through one snapshot that is closed")
  void baselineIsReadThroughOneClosedSnapshot() {
    // Given
    final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();
    fixtures.baseline(CLIENT_PATH, CLIENT);
    fixtures.working.put(CLIENT_PATH, CLIENT);

    final CountingRevisions counting = (CountingRevisions)fixtures.git;

    // When
    final GateStatus status = fixtures.run().status();

    // Then
    assertThat(status).isEqualTo(GateStatus.PASSED);
    assertThat(counting.opened).isEqualTo(1);
    assertThat(counting.closed).isEqualTo(1);
  }
}
