package dev.ward.infrastructure.finding.dotnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineHits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoberturaXmlLineCoverageTest {
  private static final int LINE_A = 3;
  private static final int LINE_B = 4;
  private static final int LINE_C = 9;
  private static final String REPORT =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <coverage line-rate="0.5" version="1.9"><packages><package name="P"><classes>
        <class name="P.A" filename="src/A.cs"><methods/><lines>
          <line number="3" hits="1" branch="True" condition-coverage="50% (1/2)"/>
          <line number="4" hits="0" branch="False"/></lines></class>
        <class name="P.A/&lt;&gt;c" filename="src/A.cs"><lines>
          <line number="9" hits="2" branch="False"/></lines></class>
      </classes></package></packages></coverage>
      """;

  @TempDir Path root;

  private final CoberturaXmlLineCoverage reader = new CoberturaXmlLineCoverage();

  @Test
  void readsHitsAndBranchesPerClassKeepingTheFilePath() throws IOException {
    final Path file = root.resolve("c.xml");
    Files.writeString(file, REPORT);

    final LineHits hits = reader.read(file);

    assertThat(hits.recordsBranches()).isTrue();
    assertThat(hits.entries()).extracting(LineHits.Entry::name).containsExactly(
        "src/A.cs",
        "src/A.cs"
    );
    assertThat(hits.entries().get(0).kind()).isEqualTo(LineHits.Kind.PATH);
    assertThat(hits.entries().get(0).lines().coverable()).containsExactly(LINE_A, LINE_B);
    assertThat(hits.entries().get(0).lines().covered()).containsExactly(LINE_A);
    assertThat(hits.entries().get(0).lines().branchCount()).isEqualTo(2);
    assertThat(hits.entries().get(0).lines().coveredBranchCount()).isEqualTo(1);
    assertThat(hits.entries().get(1).lines().covered()).containsExactly(LINE_C);
  }

  @Test
  void acceptsACoverageRootOnly() {
    assertThat(reader.accepts("<?xml version=\"1.0\"?>\n<coverage line-rate=\"1\">")).isTrue();
    assertThat(reader.accepts("<?xml version=\"1.0\"?><report name=\"x\">")).isFalse();
  }

  @Test
  void aWrongRootIsNotACoberturaReport() throws IOException {
    final Path file = root.resolve("r.xml");
    Files.writeString(file, "<report name=\"x\"/>");

    assertThatThrownBy(() -> reader.read(file))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("not a Cobertura report");
  }
}
