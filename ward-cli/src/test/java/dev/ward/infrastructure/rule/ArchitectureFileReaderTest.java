package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.application.gate.ArchitectureFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape checks of {@code ward-architecture.toml}, one problem at a time. */
class ArchitectureFileReaderTest {
  private static final String VALID_RULE =
      """
      [[rule]]
      name = "domain-is-pure"
      select = { directories = ["domain"] }
      must-not-depend-on = { directories = ["infrastructure", "adapter"] }
      because = "the domain must not know its adapters"
      """;

  @TempDir Path root;

  @Test
  void readsTheDefaultFileWhenItExists() throws IOException {
    write("ward-architecture.toml", "version = 1\n\n" + VALID_RULE);

    final ArchitectureFile file = ArchitectureFileReader.read(root, null).orElseThrow();

    assertThat(file.problems()).isEmpty();
    assertThat(file.rules()).hasSize(1);
    final ArchitectureRule rule = file.rules().getFirst();
    assertThat(rule.name()).isEqualTo("domain-is-pure");
    assertThat(rule.constraint().kind()).isEqualTo(Kind.MUST_NOT_DEPEND_ON);
    assertThat(rule.constraint().target().directories())
        .containsExactly("infrastructure", "adapter");
    assertThat(rule.allowEmpty()).isFalse();
  }

  @Test
  void anAbsentDefaultFileIsNotAnError() {
    assertThat(ArchitectureFileReader.read(root, null)).isEmpty();
  }

  @Test
  void aConfiguredPathIsReadInsteadOfTheDefault() throws IOException {
    write("ward-architecture.toml", "version = 1\n");
    write("config/architecture.toml", "version = 1\n\n" + VALID_RULE);

    final ArchitectureFile file =
        ArchitectureFileReader.read(root, "config/architecture.toml").orElseThrow();

    assertThat(file.name()).isEqualTo("config/architecture.toml");
    assertThat(file.rules()).hasSize(1);
  }

  @Test
  void aConfiguredFileThatDoesNotExistNamesTheFile() {
    assertThat(problems("config/architecture.toml"))
        .containsExactly("config/architecture.toml: the file does not exist");
  }

  @Test
  void aPathOutsideTheProjectIsRejected() {
    assertThat(problems("../shared/architecture.toml"))
        .containsExactly("../shared/architecture.toml: the file must stay inside the project");
  }

  @Test
  void textThatIsNotTomlNamesTheFileAndThePosition() throws IOException {
    assertThat(problemsOf("version = = 1\n"))
        .singleElement()
        .asString()
        .startsWith("ward-architecture.toml: not valid TOML at ");
  }

  @Test
  void aTopLevelCommandIsAnUnknownKey() throws IOException {
    assertThat(problemsOf("version = 1\ncommand = [\"rm\"]\n"))
        .singleElement()
        .asString()
        .contains("ward-architecture.toml", "command", "unknown key");
  }

  @Test
  void anUnsupportedVersionIsAProblem() throws IOException {
    assertThat(problemsOf("version = 2\n")).singleElement().asString().contains("version");
    assertThat(problemsOf("")).singleElement().asString().contains("version");
  }

  @Test
  void anUnknownPredicateNamesTheRuleAndTheKey() throws IOException {
    assertThat(problemsOf(rule("annotated-with = \"Entity\"")))
        .anySatisfy(
            problem ->
                assertThat(problem)
                    .contains("rule 'r'", "annotated-with", "unknown key", "must-not-depend-on"));
  }

  @Test
  void twoConstraintsNameBothKeys() throws IOException {
    assertThat(problemsOf(rule("file-name = \"*.java\"")))
        .singleElement()
        .asString()
        .contains("rule 'r'", "must-not-depend-on", "file-name", "exactly one constraint");
  }

  @Test
  void aMissingReasonNamesTheKey() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
            + "must-be-in = { directories = [\"b\"] }\n";

    assertThat(problemsOf(text))
        .singleElement()
        .asString()
        .contains("rule 'r'", "because", "required");
  }

  @Test
  void aMissingConstraintListsTheChoices() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\nbecause = \"x\"\n";

    assertThat(problemsOf(text)).singleElement().asString().contains("constraint", "file-name");
  }

  @Test
  void aRepeatedNameIsAProblem() throws IOException {
    assertThat(problemsOf("version = 1\n" + VALID_RULE + VALID_RULE))
        .singleElement()
        .asString()
        .contains("rule 'domain-is-pure'", "name", "another rule");
  }

  @Test
  void aSelectorWithoutPathsOrDirectoriesIsAProblem() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { exclude-paths = [\"a/**\"] }\n"
            + "file-name = \"*.java\"\nbecause = \"x\"\n";

    assertThat(problemsOf(text))
        .singleElement()
        .asString()
        .contains("rule 'r'", "select", "paths or directories");
  }

  @Test
  void aSelectorKeyOfTheConstraintIsChecked() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
            + "must-be-in = { dirs = [\"b\"] }\nbecause = \"x\"\n";

    assertThat(problemsOf(text))
        .anySatisfy(problem -> assertThat(problem).contains("must-be-in.dirs", "unknown key"));
  }

  @Test
  void aBadGlobAndABadListNameTheKey() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { paths = [\"{a\"], directories = [1] }\n"
            + "file-name = \"a/b\"\nbecause = \"x\"\n";

    assertThat(problemsOf(text))
        .anySatisfy(problem -> assertThat(problem).contains("select.paths", "not a valid glob"))
        .anySatisfy(problem -> assertThat(problem).contains("select.directories", "non-empty"))
        .anySatisfy(problem -> assertThat(problem).contains("file-name", "without '/'"));
  }

  @Test
  void ruleMustBeATableArrayAndAllowEmptyABoolean() throws IOException {
    assertThat(problemsOf("version = 1\nrule = 3\n")).singleElement().asString().contains("rule");
    assertThat(problemsOf(rule("allow-empty = \"yes\"")))
        .anySatisfy(problem -> assertThat(problem).contains("allow-empty", "true or false"));
  }

  @Test
  void allowEmptyAndEveryConstraintKindAreRead() throws IOException {
    write(
        "ward-architecture.toml",
        """
        version = 1

        [[rule]]
        name = "a"
        select = { paths = ["src/**"], exclude-paths = ["src/test/**"] }
        file-name = "*Controller.*"
        allow-empty = true
        because = "x"

        [[rule]]
        name = "b"
        select = { directories = ["x"] }
        only-accessed-by = { directories = ["y"] }
        because = "x"
        """);

    final List<ArchitectureRule> rules =
        ArchitectureFileReader.read(root, null).orElseThrow().rules();

    assertThat(rules)
        .extracting(rule -> rule.constraint().kind())
        .containsExactly(Kind.FILE_NAME, Kind.ONLY_ACCESSED_BY);
    assertThat(rules.getFirst().allowEmpty()).isTrue();
    assertThat(rules.getFirst().select().excludePaths()).containsExactly("src/test/**");
  }

  private static String rule(final String extra) {
    return "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
        + "must-not-depend-on = { directories = [\"b\"] }\nbecause = \"x\"\n"
        + extra
        + "\n";
  }

  private List<String> problems(final String relative) {
    final Optional<ArchitectureFile> file = ArchitectureFileReader.read(root, relative);
    return file.orElseThrow().problems();
  }

  private List<String> problemsOf(final String text) throws IOException {
    write("ward-architecture.toml", text);
    return problems(null);
  }

  private void write(final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
