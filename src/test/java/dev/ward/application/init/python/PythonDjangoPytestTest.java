package dev.ward.application.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A Django project whose own pytest config names no settings gets --ds on the test command. */
class PythonDjangoPytestTest {
  @Test
  void pytestGetsTheDjangoSettingsWhenTheProjectOwnsItsPytestConfig() {
    final List<InitGateOption> gates =
        new PythonGates()
            .analysisGates(
                new PythonTools(
                    BuildTool.UV,
                    true,
                    QualityPreset.STANDARD,
                    Framework.DJANGO,
                    false,
                    false,
                    false,
                    Optional.of("mysite.settings")));

    assertThat(command(gates, "pytest")).contains("--ds=mysite.settings");
  }

  private static List<String> command(final List<InitGateOption> gates, final String id) {
    return gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().command();
  }
}
