package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Framework;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Django is read from declared packages or a manage.py; its settings module from manage.py. */
class PythonFrameworksTest {
  @TempDir Path root;

  @Test
  void djangoDeclaredInPyprojectIsDjango() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"shop\"\ndependencies = [\"Django>=5.2\"]\n");

    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.DJANGO);
  }

  @Test
  void djangoInRequirementsIsDjango() throws IOException {
    Files.writeString(root.resolve("requirements-dev.txt"), "django==5.2.8\n");

    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.DJANGO);
  }

  @Test
  void manageFileAloneIsDjango() throws IOException {
    Files.writeString(root.resolve("manage.py"), "print('hi')\n");

    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.DJANGO);
  }

  @Test
  void projectWithoutAFrameworkIsNone() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"shop\"\ndependencies = [\"requests\"]\n");

    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.NONE);
  }

  @Test
  void fastapiDeclaredIsFastApi() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"app\"\ndependencies = [\"fastapi==0.143.2\"]\n");

    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.FASTAPI);
  }

  @Test
  void flaskDeclaredIsFlaskUnlessDjangoOrFastapiToo() throws IOException {
    Files.writeString(root.resolve("requirements.txt"), "Flask==3.1.3\n");
    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.FLASK);

    Files.writeString(root.resolve("requirements.txt"), "Flask==3.1.3\nfastapi==0.143.2\n");
    assertThat(PythonFrameworks.of(root)).isEqualTo(Framework.FASTAPI);
  }

  @Test
  void settingsModuleComesFromManagePy() throws IOException {
    Files.writeString(
        root.resolve("manage.py"),
        "os.environ.setdefault(\"DJANGO_SETTINGS_MODULE\", \"mysite.settings\")\n");

    assertThat(PythonFrameworks.djangoSettings(root)).contains("mysite.settings");
  }

  @Test
  void noSettingsModuleWithoutManagePy() {
    assertThat(PythonFrameworks.djangoSettings(root)).isEmpty();
  }
}
