package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs the gate over the multi-module builds under {@code src/test/resources/api-compat-modules}:
 * each has a {@code baseline} tree (the tagged revision) and the {@code compatible} and {@code
 * incompatible} trees (the working tree).
 */
class ApiCompatGateModuleFixturesTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat-modules");

  private GateResult run(final String build, final String revision) throws IOException {
    final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();
    for (final Path file : files(ROOT.resolve(build).resolve("baseline"))) {
      fixtures.baseline(relative(build, "baseline", file), Files.readString(file));
    }
    for (final Path file : files(ROOT.resolve(build).resolve(revision))) {
      fixtures.working.put(relative(build, revision, file), Files.readString(file));
    }
    return fixtures.run();
  }

  @ParameterizedTest
  @ValueSource(strings = {"maven", "gradle"})
  void compatibleRevisionPasses(final String build) throws IOException {
    final GateResult result = run(build, "compatible");

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("2 API types");
  }

  @ParameterizedTest
  @ValueSource(strings = {"maven", "gradle"})
  void incompatibleRevisionNamesTheModuleAndTheMethod(final String build) throws IOException {
    final GateResult result = run(build, "incompatible");

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items()).hasSize(1);
    assertThat(result.findings().items().getFirst().message())
        .startsWith("[core] shop.core.Cart#clear");
    assertThat(result.findings().items().getFirst().file())
        .isEqualTo("core/src/main/java/shop/core/Cart.java");
  }

  @Test
  void fixturesExist() {
    assertThat(ROOT).isDirectory();
  }

  private static String relative(final String build, final String revision, final Path file) {
    return ROOT.resolve(build).resolve(revision).relativize(file).toString().replace('\\', '/');
  }

  private static Iterable<Path> files(final Path directory) throws IOException {
    try (Stream<Path> walk = Files.walk(directory)) {
      return walk.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
