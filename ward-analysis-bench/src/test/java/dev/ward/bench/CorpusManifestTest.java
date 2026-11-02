package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusManifestTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String HEADER = "language,name,url,commit";

  @Test
  void parsesRowsAfterTheHeader() {
    final List<CorpusEntry> entries =
        CorpusManifest.parse(List.of(HEADER, "java,petclinic,https://example.com/p.git," + SHA));

    assertThat(entries)
        .containsExactly(new CorpusEntry("java", "petclinic", "https://example.com/p.git", SHA));
  }

  @Test
  void rejectsAMissingHeader() {
    assertThatThrownBy(
            () -> CorpusManifest.parse(List.of("java,petclinic,https://example.com/p.git," + SHA)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("header");
  }

  @Test
  void rejectsARowWithTheWrongColumnCount() {
    assertThatThrownBy(() -> CorpusManifest.parse(List.of(HEADER, "java,petclinic,url")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABranchNameInPlaceOfAFullSha() {
    assertThatThrownBy(() -> CorpusManifest.parse(List.of(HEADER, "java,petclinic,url,main")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("full SHA");
  }

  @Test
  void rejectsANameThatIsNotAPlainDirectory() {
    assertThatThrownBy(() -> new CorpusEntry("java", "../escape", "url", SHA))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
