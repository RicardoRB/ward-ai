package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The manifest texts a project check reads. */
final class ProjectFilesTest {

  @Test
  void readsTextByPathAndReportsAbsentPaths() {
    final ProjectFiles files = new ProjectFiles(Map.of("package.json", "{}"));

    assertThat(files.text("package.json")).contains("{}");
    assertThat(files.text("pnpm-workspace.yaml")).isEmpty();
    assertThat(files.contains("package.json")).isTrue();
    assertThat(files.contains("pnpm-workspace.yaml")).isFalse();
    assertThat(files.paths()).containsExactly("package.json");
  }

  @Test
  void emptyHasNoFiles() {
    assertThat(ProjectFiles.empty().paths()).isEmpty();
  }

  @Test
  void copiesTheMapSoLaterChangesDoNotShowThrough() {
    final Map<String, String> source = new HashMap<>();
    source.put("a/package.json", "{}");
    final ProjectFiles files = new ProjectFiles(source);
    source.put("b/package.json", "{}");

    assertThat(files.paths()).containsExactly("a/package.json");
  }

  @Test
  void keepsPathsSortedAndUnmodifiable() {
    final ProjectFiles files = new ProjectFiles(Map.of("z.json", "1", "a.json", "2"));

    assertThat(files.paths()).containsExactly("a.json", "z.json");
    assertThatThrownBy(() -> files.paths().add("m.json"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMissingTexts() {
    final Map<String, String> withNull = new HashMap<>();
    withNull.put("package.json", null);

    assertThatThrownBy(() -> new ProjectFiles(withNull)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ProjectFiles((Map<String, String>) null))
        .isInstanceOf(NullPointerException.class);
  }
}
