package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The dependency-cruiser config each style renders, with the same layers as the ArchUnit test. */
class DependencyCruiserConfigSourceTest {
  private static final QualityPreset.ArchitectureExtras STANDARD =
      new QualityPreset.ArchitectureExtras(false, false, false);
  private static final QualityPreset.ArchitectureExtras STRICT =
      new QualityPreset.ArchitectureExtras(true, true, true);

  @ParameterizedTest
  @CsvSource({
    "layered, '(^|/)(controller)/', '(^|/)(repository)/'",
    "hexagonal, '(^|/)(domain|application)/', '(^|/)(adapter|infrastructure)/'",
    "clean, '(^|/)(domain|core)/', '(^|/)(controller|adapter|infrastructure)/'"
  })
  void eachStyleForbidsTheArchUnitDependency(
      final String style, final String from, final String to) {
    final String config = DependencyCruiserConfigSource.render(style, STANDARD, true, true);

    assertThat(config)
        .startsWith(DependencyCruiserConfigSource.MARKER)
        .contains(
            "from: { path: \"" + from + "\" }",
            "to: { path: \"" + to + "\" }",
            "severity: \"error\"");
  }

  @Test
  void strictAddsNoCircularAndStandardDoesNot() {
    assertThat(DependencyCruiserConfigSource.render("layered", STRICT, false, false))
        .contains("name: \"no-circular\"");
    assertThat(DependencyCruiserConfigSource.render("layered", STANDARD, false, false))
        .doesNotContain("no-circular");
  }

  @Test
  void typeScriptCountsTypeOnlyImportsAndNamesTsconfigOnlyWhenItExists() {
    final String withTsconfig =
        DependencyCruiserConfigSource.render("hexagonal", STANDARD, true, true);
    final String withoutTsconfig =
        DependencyCruiserConfigSource.render("hexagonal", STANDARD, true, false);

    assertThat(withTsconfig)
        .contains("tsPreCompilationDeps: true", "tsConfig: { fileName: \"tsconfig.json\" }");
    assertThat(withoutTsconfig).contains("tsPreCompilationDeps: true").doesNotContain("tsConfig");
  }

  @Test
  void javaScriptHasNoTypeScriptOptions() {
    assertThat(DependencyCruiserConfigSource.render("hexagonal", STRICT, false, true))
        .doesNotContain("tsPreCompilationDeps", "tsConfig");
  }

  @Test
  void configNeverCruisesNodeModulesOrBuildOutput() {
    assertThat(DependencyCruiserConfigSource.render("clean", STANDARD, true, true))
        .contains(
            "doNotFollow: { path: \"node_modules\" }",
            "exclude: {",
            "path: \"(^|/)(node_modules|dist|build|coverage|reports|\\\\.ward|\\\\.stryker-tmp)/\"");
  }
}
