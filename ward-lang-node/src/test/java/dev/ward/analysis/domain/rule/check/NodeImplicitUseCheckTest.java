package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The implied uses of the dependency check: config files, scripts, peers, and built-in types. */
final class NodeImplicitUseCheckTest {

  private static final String MESSAGE = "unused {value}";
  private static final String INDEX = "index.ts";
  private static final String PACKAGE = "package.json";
  private static final String DEV_DEPENDENCIES = "devDependencies";

  @Test
  void treatsTypescriptAsUsedWhenTheWorkspaceHasATsconfig() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of(INDEX, "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        PACKAGE,
                        "{\"devDependencies\":{\"typescript\":\"^5\"}}",
                        "tsconfig.json",
                        "{}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsACoverageProviderAsUsedWhenTheVitestConfigSelectsIt() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "vitest.config.ts",
                        "import { defineConfig } from 'vitest/config';\nexport default defineConfig({ test: { coverage: { provider: 'v8' } } });",
                        PACKAGE,
                        "{\"devDependencies\":{\"vitest\":\"^3\",\"@vitest/coverage-v8\":\"^3\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsAScriptBinaryAsUsedFromItsInstalledPackage() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of(INDEX, "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        PACKAGE,
                        "{\"scripts\":{\"lint\":\"linter .\"},\"devDependencies\":{\"lint-pkg\":\"^1\"}}",
                        "node_modules/lint-pkg/package.json",
                        "{\"name\":\"lint-pkg\",\"bin\":{\"linter\":\"bin.js\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsAPeerOfAUsedPackageAsUsed() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        INDEX,
                        "import { render } from '@testing-library/react';\nexport const a = render;")),
                ProjectFixtures.manifests(
                    Map.of(
                        PACKAGE,
                        "{\"devDependencies\":{\"@testing-library/react\":\"^16\",\"react\":\"^18\"}}",
                        "node_modules/@testing-library/react/package.json",
                        "{\"name\":\"@testing-library/react\",\"peerDependencies\":{\"react\":\"*\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsTypesNodeAsUsedWhenABuiltInIsImported() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(
                    Map.of(
                        INDEX,
                        "import { readFileSync } from 'node:fs';\nexport const a = readFileSync;")),
                ProjectFixtures.manifests(
                    Map.of(PACKAGE, "{\"devDependencies\":{\"@types/node\":\"^22\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void stillReportsADevDependencyNothingUses() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of(INDEX, "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(PACKAGE, "{\"devDependencies\":{\"left-over\":\"^1\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:21");
  }

  @Test
  void treatsStrykerAsUsedWhenItsConfigSelectsARunner() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "stryker.config.mjs",
                        "export default { testRunner: 'vitest' };",
                        PACKAGE,
                        "{\"devDependencies\":{\"@stryker-mutator/core\":\"^9\",\"@stryker-mutator/vitest-runner\":\"^9\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void treatsAJestTransformAsUsedWhenTheJestConfigNamesIt() {
    final List<ProjectMatch> found =
        new NodeUnusedDependencyCheck(MESSAGE, DEV_DEPENDENCIES)
            .project(
                ProjectFixtures.trees(Map.of()),
                ProjectFixtures.manifests(
                    Map.of(
                        "jest.config.ts",
                        "export default { preset: 'ts-jest' };",
                        PACKAGE,
                        "{\"devDependencies\":{\"jest\":\"^29\",\"ts-jest\":\"^29\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
