package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Each inference row maps the command shapes it names to one type, and nothing else. */
class CommandTypeInferenceTest {

  @ParameterizedTest(name = "{1} -> {0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "format|./mvnw spotless:check",
        "format|./mvnw spotlessCheck",
        "format|npx prettier --check .",
        "format|pnpm exec biome format .",
        "checkstyle|./mvnw checkstyle:check",
        "checkstyle|./gradlew checkstyleMain",
        "pmd|./mvnw pmd:check",
        "pmd|./gradlew pmdTest",
        "spotbugs|./mvnw spotbugs:check",
        "coverage|./mvnw jacoco:check",
        "coverage|./gradlew jacocoTestCoverageVerification",
        "coverage|uv run coverage report --fail-under=85",
        "ruff-format|uv run ruff format --check .",
        "ruff-format|poetry run ruff format --check .",
        "ruff|uv run ruff check .",
        "ruff|poetry run ruff check . --output-format json",
        "mypy|uv run mypy src",
        "mypy|python -m mypy src",
        "pytest|uv run pytest -q",
        "pytest|uv run coverage run -m pytest",
        "mutmut|uv run mutmut run",
        "mutmut|poetry run mutmut run",
        "import-linter|uv run lint-imports",
        "pip-audit|uv run pip-audit",
        "mutation|./mvnw pitest:mutationCoverage",
        "stryker|npx stryker run",
        "tests|./mvnw test",
        "tests|./mvnw -q -DskipITs test",
        "tests|./gradlew test",
        "compile|./mvnw compile",
        "gitleaks|gitleaks detect --no-git",
        "semgrep|semgrep scan --config p/java",
        "trivy|trivy fs .",
        "eslint|npx eslint . --format json",
        "tsc|pnpm exec tsc --noEmit",
        "biome|npx biome check .",
        "openspec|openspec validate --all",
        "openspec|/usr/local/bin/openspec validate --all",
        "enforcer|./mvnw enforcer:enforce"
      })
  void infersTypeFromCommand(final String type, final String command) {
    assertThat(CommandTypeInference.infer(List.of(command.split(" ")))).isEqualTo(type);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "./mvnw -Dtest=ArchitectureBoundaryTest test",
        "./gradlew test --tests com.acme.OrderTest",
        "./scripts/lint-docs.sh",
        "npm run lint",
        "./mvnw checkstyle:check spotless:check",
        "uv run mutmut results",
        "uv run ruff check . uv run mypy src"
      })
  void unrecognizedOrAmbiguousCommandsFallBackToCommand(final String command) {
    assertThat(CommandTypeInference.infer(List.of(command.split(" "))))
        .isEqualTo(CommandTypeInference.COMMAND);
  }

  @Test
  void testRunWithFilterIsNotTests() {
    assertThat(CommandTypeInference.infer(List.of("./mvnw", "-Dtest=Foo", "test")))
        .isNotEqualTo("tests");
  }

  @Test
  void emptyCommandIsPlainCommand() {
    assertThat(CommandTypeInference.infer(List.of())).isEqualTo(CommandTypeInference.COMMAND);
  }
}
