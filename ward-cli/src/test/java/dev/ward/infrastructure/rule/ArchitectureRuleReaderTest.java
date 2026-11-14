package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The problems of a {@code [[rule]]} table, one at a time. */
class ArchitectureRuleReaderTest {
  @TempDir Path root;

  @Test
  void anUnknownPredicateNamesTheRuleAndTheKey() throws IOException {
    assertThat(
            ArchitectureFiles.problemsOf(
                root, ArchitectureFiles.rule("annotated-with = \"Entity\"")))
        .anySatisfy(
            problem ->
                assertThat(problem)
                    .contains("rule 'r'", "annotated-with", "unknown key", "must-not-depend-on"));
  }

  @Test
  void twoConstraintsNameBothKeys() throws IOException {
    assertThat(ArchitectureFiles.problemsOf(root, ArchitectureFiles.rule("file-name = \"*.java\"")))
        .singleElement()
        .asString()
        .contains("rule 'r'", "must-not-depend-on", "file-name", "exactly one constraint");
  }

  @Test
  void aMissingReasonNamesTheKey() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
            + "must-be-in = { directories = [\"b\"] }\n";

    assertThat(ArchitectureFiles.problemsOf(root, text))
        .singleElement()
        .asString()
        .contains("rule 'r'", "because", "required");
  }

  @Test
  void aMissingConstraintListsTheChoices() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\nbecause = \"x\"\n";

    assertThat(ArchitectureFiles.problemsOf(root, text))
        .singleElement()
        .asString()
        .contains("constraint", "file-name");
  }

  @Test
  void aRepeatedNameIsAProblem() throws IOException {
    assertThat(
            ArchitectureFiles.problemsOf(
                root,
                "version = 1\n" + ArchitectureFiles.VALID_RULE + ArchitectureFiles.VALID_RULE))
        .singleElement()
        .asString()
        .contains("rule 'domain-is-pure'", "name", "another rule");
  }
}
