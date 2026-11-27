package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeMemberLookups.CHECK;
import static dev.ward.analysis.domain.node.NodeMemberLookups.LOOKUP;
import static dev.ward.analysis.domain.node.NodeMemberLookups.REGISTRY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A key that a lookup in a collection of the program accepted, and a null check proved, is clean;
 * a weakened check or collection keeps the key tainted.
 */
class NodeMemberLookupTest {
  @Test
  void aKeyThatWasLookedUpInACollectionOfTheProgramAndChecked() {
    assertThat(NodeMemberLookups.found("")).isZero();
  }

  @Test
  void aFalsyCheckThatExitsProvesTheSame() {
    assertThat(
        NodeMemberLookups.found(REGISTRY, LOOKUP, CHECK.replace("found == null", "!found"))
    ).isZero();
  }

  @Test
  void aCheckWhoseNullBranchDoesNotExitStillReports() {
    assertThat(
        NodeMemberLookups.found(
            REGISTRY,
            LOOKUP,
            CHECK.replace("      return;\n    }\n  } catch", "    }\n  } catch")
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aCollectionThatARequestWritesToStillReports() {
    assertThat(
        NodeMemberLookups.found("function remember(req) {\n  registry.set(req.query, 'x');\n}\n")
    )
        .isEqualTo(1);
  }

  @Test
  void aCollectionThatAnAliasWritesToStillReports() {
    assertThat(
        NodeMemberLookups.found(
            "async function leak(req) {\n  const all = await load();\n  all.set(req.query, 'x');\n}\n"
        )
    ).isEqualTo(1);
  }

  @Test
  void aCollectionThatAnAliasWritesAProgramKeyToStaysProven() {
    assertThat(
        NodeMemberLookups.found(
            "async function add() {\n  const all = await load();\n  all.set('c', 'x');\n}\n"
        )
    )
        .isZero();
  }
}
