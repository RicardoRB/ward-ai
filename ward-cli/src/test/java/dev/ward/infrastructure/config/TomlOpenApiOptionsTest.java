package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlOpenApiOptionsTest {
  private static final String SPEC = "src/main/resources/openapi.yml";
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void readsSpecAndDefaultsBaseToOriginMain() throws IOException {
    final WardConfiguration configuration =
        read(
            reader,
            dir,
            "version = 2\n[checks.openapi-lint]\nuses = \"openapi-lint\"\ncommand = \"vacuum lint -z -n warn\"\n"
                + "spec = \""
                + SPEC
                + "\"\n");

    assertThat(configuration.checks().get("openapi-lint").openApi())
        .isEqualTo(new OpenApiGateOptions(SPEC, "origin/main"));
  }

  @Test
  void readsConfiguredBase() throws IOException {
    final WardConfiguration configuration =
        read(
            reader,
            dir,
            "version = 2\n[checks.openapi-compat]\nuses = \"openapi-compat\"\ncommand = \"oasdiff breaking --fail-on"
                + " ERR\"\nspec = \"openapi.yaml\"\nbase = \"upstream/develop\"\n");

    assertThat(configuration.checks().get("openapi-compat").openApi().base())
        .isEqualTo("upstream/develop");
  }

  @Test
  void rejectsEnabledOpenApiGateWithoutSpec() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.openapi-diff]\nuses = \"openapi-diff\"\n"
                        + "command = \"oasdiff breaking --fail-on ERR\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.openapi-diff.spec");
  }

  @Test
  void acceptsDisabledOpenApiGateWithoutSpec() throws IOException {
    final WardConfiguration configuration =
        read(
            reader,
            dir,
            "version = 2\n[checks.openapi-diff]\nuses = \"openapi-diff\"\nenabled = false\ncommand = \"oasdiff\"\n");

    assertThat(configuration.checks().get("openapi-diff").openApi()).isNull();
  }

  @Test
  void rejectsNonStringOptions() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.openapi-lint]\nuses = \"openapi-lint\"\ncommand = \"vacuum\"\nspec = 5\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.openapi-lint.spec")
        .hasMessageContaining("non-blank string");
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.openapi-compat]\nuses = \"openapi-compat\"\ncommand = \"oasdiff\"\n"
                        + "spec = \"openapi.yml\"\nbase = \" \"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.openapi-compat.base");
  }

  @Test
  void otherGatesHaveNoOpenApiOptions() throws IOException {
    final WardConfiguration configuration =
        read(reader, dir, "version = 2\n[checks.tests]\ncommand = \"x\"\nspec = \"a\"\n");

    assertThat(configuration.checks().get("tests").openApi()).isNull();
  }
}
