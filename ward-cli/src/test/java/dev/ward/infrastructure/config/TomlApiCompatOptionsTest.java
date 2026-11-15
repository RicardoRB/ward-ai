package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlApiCompatOptionsTest {
  private static final String HEADER = "version = 2\n[checks.api-compat]\nuses = \"api-compat\"\n";
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void gateNeedsNoCommandAndDefaultsEverything() throws IOException {
    final WardConfiguration configuration = read(reader, dir, HEADER);

    assertThat(configuration.checks().get("api-compat").command()).isEmpty();
    assertThat(configuration.checks().get("api-compat").apiCompat())
        .isEqualTo(new ApiCompatOptions(null, List.of()));
  }

  @Test
  void readsBaseAndInclude() throws IOException {
    final WardConfiguration configuration =
        read(
            reader,
            dir,
            HEADER + "base = \"origin/main\"\ninclude = [\"com.acme.api\", \"com.acme.spi\"]\n");

    assertThat(configuration.checks().get("api-compat").apiCompat())
        .isEqualTo(new ApiCompatOptions("origin/main", List.of("com.acme.api", "com.acme.spi")));
  }

  @Test
  void readsModulesAndRejectsBadOnes() throws IOException {
    assertThat(
            read(reader, dir, HEADER + "modules = [\"core\", \"api/spi\"]\n")
                .checks()
                .get("api-compat")
                .apiCompat()
                .modules())
        .containsExactly("core", "api/spi");
    assertThatThrownBy(() -> read(reader, dir, HEADER + "modules = []\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.modules");
    assertThatThrownBy(() -> read(reader, dir, HEADER + "modules = \"core\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.modules");
  }

  @Test
  void rejectsBlankBaseAndBadInclude() {
    assertThatThrownBy(() -> read(reader, dir, HEADER + "base = \" \"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.base");
    assertThatThrownBy(() -> read(reader, dir, HEADER + "include = []\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.include");
    assertThatThrownBy(() -> read(reader, dir, HEADER + "include = [\"\"]\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.include");
    assertThatThrownBy(() -> read(reader, dir, HEADER + "include = \"com.acme\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.api-compat.include");
  }

  @Test
  void commandVariantKeepsItsCommandAndOtherGatesHaveNoOptions() throws IOException {
    final WardConfiguration configuration =
        read(reader, dir, HEADER + "command = \"gradle apiCheck\"\n");

    assertThat(configuration.checks().get("api-compat").command())
        .containsExactly("gradle", "apiCheck");
  }
}
