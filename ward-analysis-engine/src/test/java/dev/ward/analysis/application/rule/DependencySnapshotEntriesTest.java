package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The snapshot keeps the entry files of its run, and has none when built without them. */
class DependencySnapshotEntriesTest {

  @Test
  void theTwoArgumentFormHasNoEntries() {
    final DependencySnapshot snapshot =
        new DependencySnapshot(new DependencyGraph(List.of()), Map.of());

    assertThat(snapshot.entries()).isEmpty();
    assertThat(DependencySnapshot.EMPTY.entries()).isEmpty();
  }

  @Test
  void copiesItsEntries() {
    final Set<String> entries = new HashSet<>(Set.of("src/index.ts"));
    final DependencySnapshot snapshot =
        new DependencySnapshot(new DependencyGraph(List.of()), Map.of(), entries);

    entries.add("src/other.ts");

    assertThat(snapshot.entries()).containsExactly("src/index.ts");
  }
}
