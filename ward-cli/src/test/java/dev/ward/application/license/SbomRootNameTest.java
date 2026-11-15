package dev.ward.application.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SbomRootNameTest {
  private static final Path DIRECTORY = Path.of("/work/app");

  @Test
  void theRootNameIsTheDeclaredNameInAnyDirectory() {
    final SbomService named = named(Optional.of("shop"));

    final String first = named.generate(Path.of("/a/one"), Set.of(), Optional.empty()).content();
    final String second = named.generate(Path.of("/b/two"), Set.of(), Optional.empty()).content();

    assertThat(first)
        .isEqualTo(second)
        .contains("\"name\": \"shop\"")
        .doesNotContain("\"name\": \"one\"");
  }

  @Test
  void theNameOptionWinsOverTheDeclaredName() {
    final String json =
        named(Optional.of("shop")).generate(DIRECTORY, Set.of(), Optional.of("custom")).content();

    assertThat(json).contains("\"name\": \"custom\"").doesNotContain("shop");
  }

  @Test
  void theRootNameFallsBackToTheDirectoryName() {
    final String json =
        named(Optional.empty())
            .generate(Path.of("/a/folder"), Set.of(), Optional.empty())
            .content();

    assertThat(json).contains("\"name\": \"folder\"");
  }

  private static SbomService named(final Optional<String> declared) {
    return new SbomService(
        LicenseFixtures.detector(),
        LicenseFixtures.mixed(),
        Map.of(Toolchain.NODE, project -> declared),
        "1.2.3");
  }
}
