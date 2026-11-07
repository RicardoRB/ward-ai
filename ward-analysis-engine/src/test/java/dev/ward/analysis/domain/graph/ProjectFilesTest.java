package dev.ward.analysis.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The file universe of a run copies its sets, so a later change to them does not leak in. */
class ProjectFilesTest {

  @Test
  void copiesItsSets() {
    final Set<String> files = new HashSet<>(Set.of("a.ts"));
    final ProjectFiles project = new ProjectFiles(files, Set.of("a.ts"));

    files.add("b.ts");

    assertThat(project.files()).containsExactly("a.ts");
    assertThat(project.entries()).containsExactly("a.ts");
  }

  @Test
  void emptyHasNoFilesAndNoEntries() {
    assertThat(ProjectFiles.EMPTY.files()).isEmpty();
    assertThat(ProjectFiles.EMPTY.entries()).isEmpty();
  }

  @Test
  void rejectsMissingSets() {
    assertThatNullPointerException().isThrownBy(() -> new ProjectFiles(null, Set.of()));
    assertThatNullPointerException().isThrownBy(() -> new ProjectFiles(Set.of(), null));
  }
}
