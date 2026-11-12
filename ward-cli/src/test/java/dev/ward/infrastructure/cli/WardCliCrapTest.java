package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The {@code crap} check through the real command line, on the Maven fixture. */
@DisabledOnOs(OS.WINDOWS)
class WardCliCrapTest extends WardCliIntegrationSupport {

  private static final String CART =
      """
      package com.shop;

      public class Cart {
        public int total(int a) {
          if (a > 1) { a++; }
          if (a > 2) { a++; }
          if (a > 3) { a++; }
          if (a > 4) { a++; }
          if (a > 5) { a++; }
          return a;
        }
      }
      """;

  private void projectWithUntestedMethod() throws IOException {
    final Path source = project.resolve("src/main/java/com/shop/Cart.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, CART);
    Files.setLastModifiedTime(source, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
    final Path report = project.resolve("target/site/jacoco/jacoco.xml");
    Files.createDirectories(report.getParent());
    Files.copy(Path.of("src/test/resources/coverage/jacoco-crap.xml"), report);
    Files.writeString(
        project.resolve(WARD_TOML),
        """
        version = 2
        [checks.crap]
        uses = "crap"
        """);
  }

  @Test
  void jsonStdoutHoldsOnlyTheReportWithTheCrapFindingAndTheRunFails() throws IOException {
    projectWithUntestedMethod();

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString()))
        .isEqualTo(ExitCodes.FAILED);

    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(report.get(CHECKS).size()).isEqualTo(1);
    assertThat(out.toString())
        .contains("\"crap\"", "src/main/java/com/shop/Cart.java", "complexity 6", "coverage 0%");
  }

  @Test
  void aMissingReportIsAnExecutionErrorNotAPass() throws IOException {
    projectWithUntestedMethod();
    Files.delete(project.resolve("target/site/jacoco/jacoco.xml"));

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString())).isNotZero();
    assertThat(out.toString()).contains("target/site/jacoco/jacoco.xml");
  }
}
