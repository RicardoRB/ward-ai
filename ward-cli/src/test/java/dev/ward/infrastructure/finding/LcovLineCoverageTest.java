package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineHits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LcovLineCoverageTest {

  @TempDir Path temp;

  private Path write(final String content) throws IOException {
    return Files.writeString(temp.resolve("lcov.info"), content);
  }

  @Test
  void readsAbsoluteAndRelativePathsAndSeveralRecords() throws IOException {
    final Path report =
        write(
            "TN:\nSF:src/cart.ts\nFN:1,f\nDA:12,3\nDA:13,0\nLF:2\nLH:1\nend_of_record\n"
                + "SF:/abs/other.ts\nDA:1,1,abcdef\nend_of_record\n");

    final LineHits hits = new LcovLineCoverage().read(report);

    assertThat(hits.entries()).hasSize(2);
    assertThat(hits.entries().get(0).kind()).isEqualTo(LineHits.Kind.PATH);
    assertThat(hits.entries().get(0).name()).isEqualTo("src/cart.ts");
    assertThat(hits.entries().get(0).lines().coverable()).containsExactly(12, 13);
    assertThat(hits.entries().get(0).lines().covered()).containsExactly(12);
    assertThat(hits.entries().get(1).name()).isEqualTo("/abs/other.ts");
    assertThat(hits.entries().get(1).lines().covered()).containsExactly(1);
    assertThat(hits.report()).isEqualTo(report);
  }

  @Test
  void aTruncatedFileIsRejected() throws IOException {
    final Path report = write("SF:src/cart.ts\nDA:12,3\n");

    assertThatThrownBy(() -> new LcovLineCoverage().read(report))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("lcov.info")
        .hasMessageContaining("truncated");
  }

  @Test
  void aMalformedLineOrAnOrphanLineIsRejected() throws IOException {
    final Path bad =
        Files.writeString(temp.resolve("bad.info"), "SF:a.ts\nDA:x,1\nend_of_record\n");
    final Path orphan = Files.writeString(temp.resolve("orphan.info"), "DA:1,1\n");

    assertThatThrownBy(() -> new LcovLineCoverage().read(bad))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("malformed");
    assertThatThrownBy(() -> new LcovLineCoverage().read(orphan))
        .isInstanceOf(LineCoverageException.class);
  }

  @Test
  void aMissingFileIsRejected() {
    assertThatThrownBy(() -> new LcovLineCoverage().read(temp.resolve("none")))
        .isInstanceOf(LineCoverageException.class);
  }

  @Test
  void acceptsOnlyTracefileHeads() {
    final LcovLineCoverage reader = new LcovLineCoverage();

    assertThat(reader.accepts("TN:\n")).isTrue();
    assertThat(reader.accepts("  SF:x")).isTrue();
    assertThat(reader.accepts("<?xml")).isFalse();
  }
}
