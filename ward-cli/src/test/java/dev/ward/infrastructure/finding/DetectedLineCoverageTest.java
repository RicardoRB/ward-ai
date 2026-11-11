package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.LineCoverageException;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DetectedLineCoverageTest {

  @TempDir Path temp;

  private final DetectedLineCoverage reader =
      new DetectedLineCoverage(List.of(new JacocoXmlLineCoverage(), new LcovLineCoverage()));

  @Test
  void choosesTheFormatFromTheContentNotTheName() throws IOException {
    final Path lcov =
        Files.writeString(temp.resolve("report.xml"), "SF:a.ts\nDA:1,1\nend_of_record\n");
    final Path xml =
        Files.writeString(
            temp.resolve("report.info"),
            "﻿<?xml version=\"1.0\"?><report><package name=\"p\"><sourcefile name=\"A.java\"/></package></report>");

    assertThat(reader.read(lcov).entries()).extracting(e -> e.name()).containsExactly("a.ts");
    assertThat(reader.read(xml).entries()).extracting(e -> e.name()).containsExactly("p/A.java");
  }

  @Test
  void aFileThatIsNeitherIsUnparseable() throws IOException {
    final Path other = Files.writeString(temp.resolve("x.txt"), "hello");

    assertThat(reader.accepts("hello")).isFalse();
    assertThatThrownBy(() -> reader.read(other))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("neither a JaCoCo XML report nor an lcov report");
  }

  @Test
  void aMissingFileIsUnreadable() {
    assertThatThrownBy(() -> reader.read(temp.resolve("none")))
        .isInstanceOf(LineCoverageException.class);
  }
}
