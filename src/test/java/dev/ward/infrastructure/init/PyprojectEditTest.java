package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reads declared distributions and edits only the {@code ward} dependency group. */
class PyprojectEditTest {
  private static final List<String> TOOLS = List.of("ruff==0.17.1", "mypy==2.4.0");

  @Test
  void declaredNamesCoverProjectGroupsAndPoetry() {
    final String toml =
        """
        [project]
        dependencies = ["Django>=5.2", "requests[socks]>=2"]

        [project.optional-dependencies]
        docs = ["sphinx_rtd_theme"]

        [dependency-groups]
        dev = ["pytest>=8"]

        [tool.poetry.dependencies]
        python = "^3.11"
        httpx = "*"

        [tool.poetry.group.lint.dependencies]
        ruff = "*"
        """;

    assertThat(PyprojectDependencies.declared(toml))
        .containsExactlyInAnyOrder(
            "django", "requests", "sphinx-rtd-theme", "pytest", "httpx", "ruff");
  }

  @Test
  void addsGroupWhenProjectHasNone() {
    final String toml = "[project]\nname = \"shop\"\n";

    assertThat(PyprojectEdit.withGroup(toml, TOOLS))
        .isEqualTo(
            """
            [project]
            name = "shop"

            [dependency-groups]
            ward = [
                "ruff==0.17.1",
                "mypy==2.4.0",
            ]
            """);
  }

  @Test
  void addsWardKeyToExistingGroupsTable() {
    final String toml = "[dependency-groups]\ndev = [\"pytest>=8\"]\n";

    assertThat(PyprojectEdit.withGroup(toml, TOOLS))
        .isEqualTo(
            """
            [dependency-groups]
            ward = [
                "ruff==0.17.1",
                "mypy==2.4.0",
            ]
            dev = ["pytest>=8"]
            """);
  }

  @Test
  void extendsExistingWardArrayAndKeepsItsEntries() {
    final String toml = "[dependency-groups]\nward = [\"pytest==9.1.1\"]\n";

    assertThat(PyprojectEdit.withGroup(toml, List.of("ruff==0.17.1")))
        .isEqualTo(
            """
            [dependency-groups]
            ward = [
                "pytest==9.1.1",
                "ruff==0.17.1",
            ]
            """);
  }

  @Test
  void keepsEveryOtherLineByteForByte() {
    final String toml =
        "# project comment\n[project]\nname = 'shop'\n\n[tool.ruff]\nline-length = 100 # kept\n";

    final String edited = PyprojectEdit.withGroup(toml, TOOLS);

    assertThat(edited).startsWith(toml);
  }

  @Test
  void refusesGroupEntryThatIsNotARequirement() {
    final String toml = "[dependency-groups]\nward = [{include-group = \"dev\"}]\n";

    assertThatThrownBy(() -> PyprojectEdit.withGroup(toml, TOOLS))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("not a requirement string");
  }

  @Test
  void rejectsInvalidTomlAsDeclaredNames() {
    assertThatThrownBy(() -> PyprojectDependencies.declared("[project\nname ="))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("not valid TOML");
  }

  @Test
  void normalizesRequirementNames() {
    assertThat(PyprojectDependencies.nameOf("Typing_Extensions>=4 ; python_version<'3.12'"))
        .contains("typing-extensions");
    assertThat(PyprojectDependencies.nameOf("# just a comment")).isEmpty();
    assertThat(Set.copyOf(PyprojectDependencies.declared("[project]\ndependencies = []")))
        .isEmpty();
  }
}
