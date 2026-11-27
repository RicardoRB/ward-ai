package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeListingLookups.EXITING;
import static dev.ward.analysis.domain.node.NodeListingLookups.READ;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The near variants of {@link NodeListingLookupTest} that change the caller. */
class NodeListingLookupCallerTest {
  @Test
  void aKeyThatIsNotTheCheckedOneStillReports() {
    assertThat(NodeListingLookups.found(READ, EXITING.replace("sink(key)", "sink(req.query)")))
        .isEqualTo(1);
  }

  @Test
  void aCallerThatDoesNotLeaveOnAnEmptyListStillReports() {
    assertThat(NodeListingLookups.found(READ, EXITING.replace("    return;\n", "    log();\n")))
        .isEqualTo(1);
  }

  @Test
  void aCheckOfAnotherPropertyStillReports() {
    assertThat(
        NodeListingLookups.found(READ, EXITING.replace("data.fixes.length", "data.correct.length"))
    ).isEqualTo(1);
  }
}
