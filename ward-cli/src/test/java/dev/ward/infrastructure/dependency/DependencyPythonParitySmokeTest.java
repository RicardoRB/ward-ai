package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.python.PythonDependencySource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compares the Python inventory with uv. */
class DependencyPythonParitySmokeTest {
  private static final Pattern PYTHON_PIN = Pattern.compile("^([A-Za-z0-9_.-]+)==(\\S+)");

  @Test
  void uvMatchesUvExport(@TempDir final Path work) throws IOException {
    ParitySupport.enabled("uv");
    final Path dir = ParitySupport.copy("uv-single", work);
    final Set<String> listed = new TreeSet<>();
    for (final String line :
        ParitySupport.run(
                dir,
                "uv",
                "export",
                "--no-hashes",
                "--all-packages",
                "--all-groups",
                "--all-extras",
                "--frozen")
            .lines()
            .toList()) {
      final Matcher pin = PYTHON_PIN.matcher(line);
      if (pin.find()) {
        listed.add(
            pin.group(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[-_.]+", "-")
                + "@"
                + pin.group(2));
      }
    }

    final DependencyInventory inventory =
        new PythonDependencySource()
            .read(new Project(dir, Language.PYTHON, BuildTool.UV, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.PYPI)).isEqualTo(listed);
  }
}
