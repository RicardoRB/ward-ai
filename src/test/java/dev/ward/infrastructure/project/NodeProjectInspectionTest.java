package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Node package manager, language and version detection from project files. */
class NodeProjectInspectionTest {
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path dir;

  @ParameterizedTest
  @CsvSource({
    "pnpm@9.12.0, package-lock.json, PNPM",
    "bun, package-lock.json, BUN",
    "deno@2.0.0, yarn.lock, YARN"
  })
  void packageManagerFieldWinsUnlessUnknown(
      final String declared, final String lockFile, final BuildTool tool) throws IOException {
    write(PACKAGE_JSON, "{\"packageManager\": \"" + declared + "\"}");
    write(lockFile, "");

    assertThat(detect().buildTool()).isEqualTo(tool);
  }

  @ParameterizedTest
  @CsvSource({
    "bun.lock, BUN",
    "bun.lockb, BUN",
    "pnpm-lock.yaml, PNPM",
    "yarn.lock, YARN",
    "package-lock.json, NPM"
  })
  void lockFileDecidesTheManager(final String lockFile, final BuildTool tool) throws IOException {
    write(PACKAGE_JSON, "{}");
    write(lockFile, "");

    assertThat(detect().buildTool()).isEqualTo(tool);
  }

  @Test
  void bunLockWinsOverStaleNpmLock() throws IOException {
    write(PACKAGE_JSON, "{}");
    write("bun.lock", "");
    write("package-lock.json", "{}");

    assertThat(detect().buildTool()).isEqualTo(BuildTool.BUN);
  }

  @Test
  void noLockFileIsNpm() throws IOException {
    write(PACKAGE_JSON, "{\"name\": \"app\"}");

    assertThat(detect().buildTool()).isEqualTo(BuildTool.NPM);
    assertThat(detect().language()).isEqualTo(Language.JAVASCRIPT);
  }

  @Test
  void malformedPackageJsonStillDetectsTheProject() throws IOException {
    write(PACKAGE_JSON, "{ not json");
    write("pnpm-lock.yaml", "");

    final Project project = detect();

    assertThat(project.language()).isEqualTo(Language.JAVASCRIPT);
    assertThat(project.buildTool()).isEqualTo(BuildTool.PNPM);
    assertThat(project.languageVersion()).isEqualTo("24");
  }

  @Test
  void nonObjectPackageJsonIsIgnored() throws IOException {
    write(PACKAGE_JSON, "[]");

    assertThat(detect().buildTool()).isEqualTo(BuildTool.NPM);
  }

  @Test
  void tsconfigMakesTypeScript() throws IOException {
    write("tsconfig.json", "{}");
    write(PACKAGE_JSON, "{}");

    assertThat(detect().language()).isEqualTo(Language.TYPESCRIPT);
  }

  private Project detect() {
    return FileSystemProjectInspection.projectAt(dir).orElseThrow();
  }

  private void write(final String name, final String content) throws IOException {
    Files.writeString(dir.resolve(name), content);
  }
}
