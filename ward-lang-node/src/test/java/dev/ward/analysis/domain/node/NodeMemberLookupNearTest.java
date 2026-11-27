package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeMemberLookups.CHECK;
import static dev.ward.analysis.domain.node.NodeMemberLookups.LOOKUP;
import static dev.ward.analysis.domain.node.NodeMemberLookups.REGISTRY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The near variants of {@link NodeMemberLookupTest}: each weakens one link of the proof. */
class NodeMemberLookupNearTest {
  @Test
  void aCollectionHandedToAnotherFunctionStillReports() {
    assertThat(
        NodeMemberLookups.found(
            "async function hand() {\n  const all = await load();\n  use(all);\n}\n"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aCollectionThatTheFileExposesOtherwiseStillReports() {
    assertThat(
        NodeMemberLookups.found("function expose() {\n  return wrap(registry);\n}\n")
    ).isEqualTo(1);
  }

  @Test
  void aLookupWithAReturnOutsideTheMembershipBranchStillReports() {
    final String other = LOOKUP.replace("  return null;\n};", "  return fallback(key);\n};");
    assertThat(NodeMemberLookups.found(REGISTRY, other, CHECK)).isEqualTo(1);
  }

  @Test
  void aLookupThatReassignsItsKeyStillReports() {
    final String other = LOOKUP.replace("  const known", "  key = req.query;\n  const known");
    assertThat(NodeMemberLookups.found(REGISTRY, other, CHECK)).isEqualTo(1);
  }

  @Test
  void aCallerThatDoesNotAwaitTheResultStillReports() {
    assertThat(
        NodeMemberLookups.found(REGISTRY, LOOKUP, CHECK.replace("await lookup", "lookup"))
    ).isEqualTo(1);
  }

  @Test
  void aKeyThatIsNotTheCheckedOneStillReports() {
    assertThat(
        NodeMemberLookups.found(REGISTRY, LOOKUP, CHECK.replace("sink(key)", "sink(req.query)"))
    ).isEqualTo(1);
  }
}
