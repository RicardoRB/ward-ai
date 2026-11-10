package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.RuleConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleConfigFileReaderTest {

  private static final long THREE = 3L;
  private static final long EIGHTY = 80L;

  @TempDir Path root;

  @Test
  void aMissingDefaultFileIsNotAFile() {
    assertThat(RuleConfigFileReader.read(root, null)).isEmpty();
  }

  @Test
  void readsTheSelectionAndTheTables() throws IOException {
    write(
        "version = 1\n[select]\npreset = \"strict\"\nlanguages = [\"java\"]\n"
            + "include = [\"ward:java/x\"]\n[rules.\"ward:java/y\"]\nmax = 3\nglobs = [\"a\"]\n");

    final RuleConfigFile file = RuleConfigFileReader.read(root, null).orElseThrow();

    assertThat(file.problems()).isEmpty();
    assertThat(file.select().preset()).isEqualTo("strict");
    assertThat(file.select().languages()).containsExactly("java");
    assertThat(file.select().include()).containsExactly("ward:java/x");
    assertThat(file.rules().get("ward:java/y"))
        .containsEntry("max", THREE)
        .containsEntry("globs", List.of("a"));
  }

  @Test
  void reportsEveryShapeProblemWithItsTableAndKey() throws IOException {
    write(
        "version = 2\ncolor = 1\n[select]\npreset = 3\nlanguages = \"java\"\nwhat = 1\n"
            + "[rules]\n\"ward:java/y\" = 1\n");

    final List<String> problems = RuleConfigFileReader.read(root, null).orElseThrow().problems();

    assertThat(problems)
        .anyMatch(p -> p.startsWith("ward-rules.toml: version: expected 1, got 2"))
        .anyMatch(p -> p.startsWith("ward-rules.toml: color: unknown key"))
        .anyMatch(p -> p.startsWith("ward-rules.toml: [select] preset: expected a string"))
        .anyMatch(p -> p.startsWith("ward-rules.toml: [select] languages: expected a list"))
        .anyMatch(p -> p.startsWith("ward-rules.toml: [select] what: unknown key"))
        .anyMatch(p -> p.contains("[rules.\"ward:java/y\"] must be a table"));
  }

  @Test
  void theDottedFormNamesTheSameRuleAsTheQuotedForm() throws IOException {
    write("version = 1\n[rules.java.method-length]\nmax = 80\n");

    final RuleConfigFile file = RuleConfigFileReader.read(root, null).orElseThrow();

    assertThat(file.problems()).isEmpty();
    assertThat(file.rules()).containsOnlyKeys("ward:java/method-length");
    assertThat(file.rules().get("ward:java/method-length")).containsEntry("max", EIGHTY);
    assertThat(file.header("ward:java/method-length")).isEqualTo("[rules.java.method-length]");
  }

  @Test
  void aQuotedTableKeepsItsHeader() throws IOException {
    write("version = 1\n[rules.\"ward:java/method-length\"]\nmax = 80\n");

    final RuleConfigFile file = RuleConfigFileReader.read(root, null).orElseThrow();

    assertThat(file.header("ward:java/method-length"))
        .isEqualTo("[rules.\"ward:java/method-length\"]");
  }

  @Test
  void aValueDirectlyUnderAGroupIsReported() throws IOException {
    write("version = 1\n[rules.java]\nmax = 3\n");

    assertThat(RuleConfigFileReader.read(root, null).orElseThrow().problems())
        .singleElement()
        .isEqualTo("ward-rules.toml: [rules.java] max: must be a rule table");
  }

  @Test
  void aMissingVersionIsReported() throws IOException {
    write("[select]\n");

    assertThat(RuleConfigFileReader.read(root, null).orElseThrow().problems())
        .anyMatch(p -> p.contains("version: expected 1, got null"));
  }

  @Test
  void invalidTomlIsReported() throws IOException {
    write("version = [\n");

    assertThat(RuleConfigFileReader.read(root, null).orElseThrow().problems())
        .singleElement()
        .satisfies(p -> assertThat(p).startsWith("ward-rules.toml: not valid TOML"));
  }

  @Test
  void aConfiguredPathOutsideTheProjectIsRejected() {
    assertThat(RuleConfigFileReader.read(root, "../x.toml").orElseThrow().problems())
        .singleElement()
        .satisfies(p -> assertThat(p).contains("inside the project"));
  }

  private void write(final String content) throws IOException {
    Files.writeString(root.resolve("ward-rules.toml"), content);
  }
}
