package dev.ward.infrastructure.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.infrastructure.buildpolicy.gradle.GradleBuildReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildPolicyReadersTest {
  @TempDir Path root;

  private ReaderProject project() {
    return new ReaderProject(root);
  }

  @Test
  void malformedTomlAndJsonAreErrors() throws IOException {
    project().write("pyproject.toml", "[project\n");
    assertThatThrownBy(project()::all).hasMessageContaining("pyproject.toml");
    Files.delete(root.resolve("pyproject.toml"));
    project().write("package.json", "{");
    assertThatThrownBy(project()::all).hasMessageContaining("package.json");
  }

  @Test
  void gradleFormsInBothDslsAndTheCatalogAreRead() throws IOException {
    project().write("settings.gradle", "include ':api', ':core:impl'\n");
    project()
        .write(
            "build.gradle.kts",
            "plugins {\n  id(\"org.example.a\") version \"1.0\"\n  kotlin(\"jvm\") version \"2.0.0\"\n"
                + "  id(\"org.example.b\") version someVariable\n  alias(libs.plugins.tool)\n}\n"
                + "dependencies {\n  implementation(\"g:a:1.+\")\n  testImplementation(\"g:b:\\$v\")\n}\n");
    project()
        .write(
            "gradle/libs.versions.toml",
            "[versions]\nk = \"1.2\"\n[libraries]\nx = { module = \"g:x\", version.ref = \"k\" }\n"
                + "[plugins]\ntool = { id = \"org.example.tool\" }\n");
    final List<Declaration> all = project().all().declarations();
    assertThat(all)
        .extracting(Declaration::name)
        .contains(
            "org.example.a",
            "org.jetbrains.kotlin.jvm",
            "org.example.b",
            "g:a",
            "g:b",
            "g:x",
            "org.example.tool");
    assertThat(all)
        .filteredOn(d -> "org.example.b".equals(d.name()) || "g:b".equals(d.name()))
        .allSatisfy(d -> assertThat(d.flag()).isEqualTo(Declaration.Flag.UNDETERMINABLE));
    assertThat(all)
        .filteredOn(d -> "g:x".equals(d.name()))
        .allSatisfy(d -> assertThat(d.version()).isEqualTo("1.2"));
    assertThat(new GradleBuildReader().modules(root, "")).containsExactly("api", "core/impl");
  }

  @Test
  void nodeWorkspacesAndPythonRequirementsAreRead() throws IOException {
    project()
        .write(
            "package.json",
            "{\"workspaces\": [\"packages/*\"], \"devDependencies\": {\"a\": \"~1.0.0\"}}");
    project().write("packages/x/package.json", "{\"dependencies\": {\"b\": \"2.0.0\"}}");
    project()
        .write(
            "pyproject.toml",
            "[project]\ndependencies = [\"Django[argon2]>=4 ; python_version>'3'\", \"flask\"]\n"
                + "[build-system]\nrequires = [\"hatchling==1.0\"]\n[tool.poetry.dependencies]\npython = \"^3.11\"\nfoo = \"1.2.3\"\n");
    final List<Declaration> all = project().all().declarations();
    assertThat(all)
        .extracting(Declaration::name)
        .contains("a", "b", "Django", "flask", "hatchling", "foo");
    assertThat(all)
        .filteredOn(d -> "b".equals(d.name()))
        .extracting(Declaration::file)
        .containsExactly("packages/x/package.json");
    assertThat(all)
        .filteredOn(d -> "foo".equals(d.name()))
        .extracting(Declaration::version)
        .containsExactly("==1.2.3");
    assertThat(all)
        .filteredOn(d -> "flask".equals(d.name()))
        .extracting(Declaration::version)
        .containsExactly("*");
  }
}
