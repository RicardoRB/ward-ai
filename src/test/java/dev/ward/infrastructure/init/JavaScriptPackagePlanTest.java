package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dev dependencies init plans for a JavaScript project's ESLint gate. */
class JavaScriptPackagePlanTest {
  @TempDir Path root;

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), "{\"name\": \"shop\"}\n");
  }

  @Test
  void javaScriptEslintPlansGlobalsAndNoTypeScriptPackages() {
    final Project javaScript =
        new Project(
            root,
            Language.JAVASCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE);

    final PlannedFileChange plan =
        new NodeBuildToolConfigurator(
                new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", ""))
            .plan(
                javaScript,
                List.of(
                    new InitGateOption("eslint", "eslint", List.of("npx", "eslint"), true, "ok")),
                null,
                false);

    assertThat(plan.details())
        .containsExactly(
            "add devDependency eslint 10.12.0",
            "add devDependency @eslint/js 10.0.1",
            "add devDependency globals 17.13.0",
            "run npm install, which updates package-lock.json");
  }
}
