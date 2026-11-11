package dev.ward.application.init.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.init.ConfigurationTemplate;
import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The crap-report check writes the lcov file that the crap gate reads. */
class PythonCrapReportTest {
  private static final InitInputs INPUTS =
      InitInputs.withoutManifests(new FakeEnvironmentInspection("uv", "poetry", "python3"));

  @ParameterizedTest
  @CsvSource({
    "UV,'uv run --group ward coverage lcov -o .ward/reports/coverage.lcov'",
    "POETRY,'poetry run coverage lcov -o .ward/reports/coverage.lcov'",
    "PIP,'.venv/bin/coverage lcov -o .ward/reports/coverage.lcov'"
  })
  void everyManagerWritesTheReportThroughItsPrefix(final BuildTool manager, final String command) {
    final String toml = toml(manager, true);

    assertThat(toml)
        .contains(
            "[checks.crap-report]\nenabled = true\nprofiles = [\"pre-push\", \"ci\"]\ncommand = \""
                + command
                + "\"\n",
            "[checks.crap]\nenabled = true\nprofiles = [\"pre-push\", \"ci\"]\nuses = \"crap\"\n"
                + "report = \".ward/reports/coverage.lcov\"\n")
        .doesNotContain("uses = \"crap-report\"");
  }

  @Test
  void checksAreOrderedPytestCoverageReportCrap() {
    final String toml = toml(BuildTool.UV, true);

    assertThat(
            List.of("pytest", "coverage", "crap-report", "crap").stream()
                .map(id -> toml.indexOf("[checks." + id + "]"))
                .toList())
        .isSorted()
        .doesNotContain(-1);
  }

  @Test
  void noReportCheckWithoutCrap() {
    assertThat(toml(BuildTool.UV, false)).doesNotContain("crap");
  }

  @Test
  void crapNeedsPytest() {
    final Project project = project(BuildTool.UV);
    final List<InitGateOption> gates =
        new InitGeneralCatalog(INPUTS)
            .gates(project, null).stream()
                .filter(gate -> List.of("mypy", "crap").contains(gate.id()))
                .toList();

    assertThatThrownBy(
            () -> new PythonInitSupport().crap(project, INPUTS).orElseThrow().apply(gates))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'pytest'");
  }

  private static String toml(final BuildTool manager, final boolean crap) {
    final Project project = project(manager);
    final List<InitGateOption> offered =
        new InitGeneralCatalog(INPUTS)
            .gates(project, QualityPreset.STANDARD).stream()
                .filter(
                    gate ->
                        List.of("mypy", "pytest", "coverage").contains(gate.id())
                            || crap && "crap".equals(gate.id()))
                .toList();
    return ConfigurationTemplate.render(
        new PythonInitSupport().crap(project, INPUTS).orElseThrow().apply(offered),
        QualityPreset.STANDARD,
        project);
  }

  private static Project project(final BuildTool manager) {
    return new Project(Path.of("/shop"), Language.PYTHON, manager, Framework.NONE);
  }
}
