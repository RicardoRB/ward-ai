package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.finding.jvm.JavacOutputFindings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositeFindingExtractorTest {
  @TempDir Path root;

  @Test
  void ignoresGatesWithoutASourceThatReadsThem() {
    final CompositeFindingExtractor extractor =
        new CompositeFindingExtractor(List.of(new JavacOutputFindings()));

    assertThat(
            extractor.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("tests", ""),
                FindingFixtures.justBefore()))
        .isEmpty();
  }
}
