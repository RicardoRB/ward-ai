package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemPythonToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Django projects get the mypy plugin in Ward's config, and a warning for a config without it. */
class PythonDjangoConfigTest {
  @TempDir Path root;

  @Test
  void userMypyConfigWithoutThePluginIsKeptWithAWarning() throws IOException {
    Files.writeString(root.resolve("mypy.ini"), "[mypy]\nstrict = True\n");

    final List<NodeConfigTarget> targets = targets();

    assertThat(targets.getFirst().disposition()).isEqualTo(NodeConfigTarget.Disposition.KEEP);
    assertThat(targets.getFirst().detail()).contains("mypy has no Django plugin");
  }

  @Test
  void wardMypyConfigNamesTheDjangoPluginAndTheSettings() throws IOException {
    Files.writeString(
        root.resolve("manage.py"),
        "os.environ.setdefault(\"DJANGO_SETTINGS_MODULE\", \"mysite.settings\")\n");

    final String content = targets().getFirst().content();

    assertThat(content)
        .contains(
            "plugins = mypy_django_plugin.main", "django_settings_module = \"mysite.settings\"");
  }

  private List<NodeConfigTarget> targets() {
    final Project project = new Project(root, Language.PYTHON, BuildTool.UV, Framework.DJANGO);
    return PythonConfigFiles.targets(
        project,
        new FileSystemPythonToolConfigs(),
        QualityPreset.STANDARD,
        List.of(new InitGateOption("mypy", List.of(), true, "")));
  }
}
