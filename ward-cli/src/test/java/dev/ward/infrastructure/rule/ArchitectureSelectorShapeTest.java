package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The problems of selectors, globs and flags, and the accepted rule shapes. */
class ArchitectureSelectorShapeTest {
  @TempDir Path root;

  @Test
  void aSelectorWithoutPathsOrDirectoriesIsAProblem() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { exclude-paths = [\"a/**\"] }\n"
            + "file-name = \"*.java\"\nbecause = \"x\"\n";

    assertThat(ArchitectureFiles.problemsOf(root, text))
        .singleElement()
        .asString()
        .contains("rule 'r'", "select", "paths or directories");
  }

  @Test
  void aSelectorKeyOfTheConstraintIsChecked() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
            + "must-be-in = { dirs = [\"b\"] }\nbecause = \"x\"\n";

    assertThat(ArchitectureFiles.problemsOf(root, text))
        .anySatisfy(problem -> assertThat(problem).contains("must-be-in.dirs", "unknown key"));
  }

  @Test
  void aBadGlobAndABadListNameTheKey() throws IOException {
    final String text =
        "version = 1\n[[rule]]\nname = \"r\"\nselect = { paths = [\"{a\"], directories = [1] }\n"
            + "file-name = \"a/b\"\nbecause = \"x\"\n";

    assertThat(ArchitectureFiles.problemsOf(root, text))
        .anySatisfy(problem -> assertThat(problem).contains("select.paths", "not a valid glob"))
        .anySatisfy(problem -> assertThat(problem).contains("select.directories", "non-empty"))
        .anySatisfy(problem -> assertThat(problem).contains("file-name", "without '/'"));
  }

  @Test
  void ruleMustBeATableArrayAndAllowEmptyABoolean() throws IOException {
    assertThat(ArchitectureFiles.problemsOf(root, "version = 1\nrule = 3\n"))
        .singleElement()
        .asString()
        .contains("rule");
    assertThat(ArchitectureFiles.problemsOf(root, ArchitectureFiles.rule("allow-empty = \"yes\"")))
        .anySatisfy(problem -> assertThat(problem).contains("allow-empty", "true or false"));
  }

  @Test
  void allowEmptyAndEveryConstraintKindAreRead() throws IOException {
    ArchitectureFiles.write(
        root,
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
}
