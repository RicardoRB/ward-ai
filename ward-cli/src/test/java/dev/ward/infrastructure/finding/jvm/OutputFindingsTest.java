package dev.ward.infrastructure.finding.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.infrastructure.finding.FindingFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputFindingsTest {
  // Positions written into the compiler output fixtures below.
  private static final int MAVEN_ERROR_LINE = 12;
  private static final int MAVEN_ERROR_COLUMN = 8;
  private static final int GRADLE_ERROR_LINE = 7;
  @TempDir Path root;

  @Test
  void mavenJavacErrorsAreLocatedAndDeduplicated() {
    final String output =
        """
        [INFO] --- compiler:3.13.0:compile (default-compile) @ demo ---
        [ERROR] COMPILATION ERROR :
        [ERROR] %1$s/src/main/java/Foo.java:[12,8] cannot find symbol
          symbol:   class Bar
        [WARNING] %1$s/src/main/java/Old.java:[3,1] [deprecation] Thing in pkg has been deprecated
        [ERROR] Failed to execute goal ...: Compilation failure
        [ERROR] %1$s/src/main/java/Foo.java:[12,8] cannot find symbol
        """
            .formatted(root);

    final List<Finding> findings =
        new JavacOutputFindings()
            .extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("compile", output),
                FindingFixtures.justBefore());

    assertThat(findings).hasSize(2);
    final Finding error = findings.getFirst();
    assertThat(error.file()).isEqualTo("src/main/java/Foo.java");
    assertThat(error.line()).isEqualTo(MAVEN_ERROR_LINE);
    assertThat(error.column()).isEqualTo(MAVEN_ERROR_COLUMN);
    assertThat(error.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(error.ruleId()).isEqualTo("javac.error");
    assertThat(error.message()).isEqualTo("cannot find symbol");
    assertThat(error.tool()).isEqualTo("javac");
    assertThat(findings.get(1).severity()).isEqualTo(FindingSeverity.WARNING);
  }

  @Test
  void gradleJavacErrorsFromStderrWithAnsiColours() {
    final GateResult output =
        FindingFixtures.failedWithStderr(
            "compile",
            "\u001B[31m"
                + root
                + "/src/main/java/Foo.java:7: error: ';' expected\u001B[0m\n"
                + "        int x = 1\n"
                + "                 ^\n"
                + "1 error\n");

    final List<Finding> findings =
        new JavacOutputFindings()
            .extract(FindingFixtures.gradle(root), output, FindingFixtures.justBefore());

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.file()).isEqualTo("src/main/java/Foo.java");
              assertThat(finding.line()).isEqualTo(GRADLE_ERROR_LINE);
              assertThat(finding.column()).isNull();
              assertThat(finding.message()).isEqualTo("';' expected");
            });
  }

  @Test
  void unmatchedCompileOutputProducesNoFindings() {
    assertThat(
            new JavacOutputFindings()
                .extract(
                    FindingFixtures.maven(root),
                    FindingFixtures.failed("compile", "BUILD FAILURE\nsomething else"),
                    FindingFixtures.justBefore()))
        .isEmpty();
  }
}
