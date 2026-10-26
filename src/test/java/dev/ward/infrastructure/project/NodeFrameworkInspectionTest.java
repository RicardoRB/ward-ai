package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Node framework detection from the packages a project declares. */
class NodeFrameworkInspectionTest {
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path dir;

  @ParameterizedTest
  @CsvSource({
    "nuxt, VUE",
    "vue, VUE",
    "@sveltejs/kit, SVELTE",
    "svelte, SVELTE",
    "astro, ASTRO",
    "@angular/core, ANGULAR"
  })
  void declaredFrameworkPackageDecidesTheFramework(final String pkg, final Framework framework)
      throws IOException {
    write(PACKAGE_JSON, "{\"dependencies\": {\"" + pkg + "\": \"1.0.0\"}}");

    assertThat(detect().framework()).isEqualTo(framework);
  }

  @Test
  void devDependencyDeclaresTheFramework() throws IOException {
    write(PACKAGE_JSON, "{\"devDependencies\": {\"astro\": \"5.0.0\"}}");

    assertThat(detect().framework()).isEqualTo(Framework.ASTRO);
  }

  @Test
  void astroWinsOverVueWhenBothAreDeclared() throws IOException {
    write(PACKAGE_JSON, "{\"dependencies\": {\"vue\": \"3.5.0\", \"astro\": \"5.0.0\"}}");

    assertThat(detect().framework()).isEqualTo(Framework.ASTRO);
  }

  @Test
  void svelteWinsOverVueWhenBothAreDeclared() throws IOException {
    write(PACKAGE_JSON, "{\"dependencies\": {\"vue\": \"3.5.0\", \"svelte\": \"5.0.0\"}}");

    assertThat(detect().framework()).isEqualTo(Framework.SVELTE);
  }

  @Test
  void filesAloneDoNotDecideTheFramework() throws IOException {
    write(PACKAGE_JSON, "{}");
    Files.createDirectories(dir.resolve("src"));
    Files.writeString(dir.resolve("src/App.vue"), "<template />");

    assertThat(detect().framework()).isEqualTo(Framework.NONE);
  }

  @Test
  void plainTypeScriptHasNoFramework() throws IOException {
    write(PACKAGE_JSON, "{\"devDependencies\": {\"typescript\": \"5.0.0\"}}");

    assertThat(detect().framework()).isEqualTo(Framework.NONE);
  }

  private Project detect() {
    return FileSystemProjectInspection.projectAt(dir).orElseThrow();
  }

  private void write(final String name, final String content) throws IOException {
    Files.writeString(dir.resolve(name), content);
  }
}
