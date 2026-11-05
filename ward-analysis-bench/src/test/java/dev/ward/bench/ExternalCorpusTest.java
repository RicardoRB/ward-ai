package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the pinned external corpora from {@code external.csv}. */
class ExternalCorpusTest {

  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String HEADER = "name,url,commit,labels,format,root";

  @TempDir Path dir;

  @Test
  void readsOneCorpusPerRow() {
    final List<ExternalCorpus> corpora =
        ExternalCorpus.parse(
            List.of(
                HEADER,
                "owasp,https://example.com/owasp.git,"
                    + SHA
                    + ",expectedresults-1.2.csv,owasp-benchmark,src/main/java/testcode"));

    assertThat(corpora)
        .containsExactly(
            new ExternalCorpus(
                "owasp",
                "https://example.com/owasp.git",
                SHA,
                "expectedresults-1.2.csv",
                ExternalCorpus.OWASP,
                "src/main/java/testcode"));
  }

  @Test
  void rejectsAnUnknownLabelFormat() {
    assertThatThrownBy(
            () ->
                ExternalCorpus.parse(
                    List.of(HEADER, "x,https://example.com/x.git," + SHA + ",l.csv,csv,r")))
        .hasMessageContaining("Unknown label format");
  }

  @Test
  void anAbsentFileGivesNoCorpora() {
    assertThat(ExternalCorpus.read(dir.resolve("external.csv"))).isEmpty();
  }
}
