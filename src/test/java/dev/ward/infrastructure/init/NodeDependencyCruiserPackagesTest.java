package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The packages the dependency-cruiser gate adds to a TypeScript and a JavaScript project. */
class NodeDependencyCruiserPackagesTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String BARE_MANIFEST = "{\n  \"name\": \"shop\"\n}\n";

  @TempDir Path root;

  @Test
  void typeScriptProjectGetsDependencyCruiserAndTypescript() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), BARE_MANIFEST);

    configurator().apply(project(Language.TYPESCRIPT), gate());

    assertThat(Files.readString(root.resolve(PACKAGE_JSON)))
        .contains("\"dependency-cruiser\": \"18.6.0\"", "\"typescript\": \"6.0.3\"");
  }

  @Test
  void javaScriptProjectGetsOnlyDependencyCruiser() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), BARE_MANIFEST);

    configurator().apply(project(Language.JAVASCRIPT), gate());

    assertThat(Files.readString(root.resolve(PACKAGE_JSON)))
        .contains("\"dependency-cruiser\": \"18.6.0\"")
        .doesNotContain("typescript");
  }

  private NodeBuildToolConfigurator configurator() {
    return new NodeBuildToolConfigurator(
        new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", ""));
  }

  private Project project(final Language language) {
    return new Project(
        root, language, BuildTool.NPM, Framework.NONE, false, false, root, Set.of(), "24");
  }

  private static List<InitGateOption> gate() {
    return List.of(
        new InitGateOption(
            "dependency-cruiser",
            "Checks import boundaries",
            List.of("npx", "depcruise"),
            true,
            "available"));
  }
}
