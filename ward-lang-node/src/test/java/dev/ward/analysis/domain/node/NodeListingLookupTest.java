package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeListingLookups.BRANCHING;
import static dev.ward.analysis.domain.node.NodeListingLookups.EXITING;
import static dev.ward.analysis.domain.node.NodeListingLookups.READ;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A key that a cached listing proved to start the name of a directory entry is clean where the
 * caller saw the list not empty: past an exit, and in the {@code else} branch.
 */
class NodeListingLookupTest {
  @Test
  void aKeyPastAnExitOnAnEmptyListIsClean() {
    assertThat(NodeListingLookups.found(READ, EXITING)).isZero();
  }

  @Test
  void aKeyInTheElseBranchOfAnEmptyListIsClean() {
    assertThat(NodeListingLookups.found(READ, BRANCHING)).isZero();
  }

  @Test
  void aKeyInTheBranchThatSawTheEmptyListStillReports() {
    assertThat(
        NodeListingLookups.found(READ, BRANCHING.replace("respond(404)", "sink(key)"))
    ).isEqualTo(1);
  }

  @Test
  void aNegatedLengthProvesTheSame() {
    assertThat(
        NodeListingLookups.found(
            READ,
            EXITING.replace("data.fixes.length === 0", "!data.fixes.length")
        )
    ).isZero();
  }
}
