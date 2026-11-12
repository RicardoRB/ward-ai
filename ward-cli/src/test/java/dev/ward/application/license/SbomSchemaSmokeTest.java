package dev.ward.application.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates generated SBOMs against the official CycloneDX 1.6 JSON schema.
 *
 * <p>Skipped unless {@code -Dward.sbom.schema=<classpath>} names the jars of {@code
 * cyclonedx-core-java} and its dependencies (for example the output of {@code mvn
 * dependency:build-classpath} on that artifact). The validator runs as a separate {@code java}
 * source-launch process, so the library is not a Ward dependency.
 */
@EnabledIfSystemProperty(named = "ward.sbom.schema", matches = ".+")
class SbomSchemaSmokeTest {
  private static final int MINUTE = 60;
  private static final String RUNTIME = "package.json";

  @TempDir Path work;

  @Test
  void everyEcosystemAndLicenseShapeIsAValidDocument() throws Exception {
    final SbomService sbom =
        new SbomService(LicenseFixtures.detector(), mixedEcosystems(), "1.2.3");

    final Path file = work.resolve("sbom.json");
    Files.writeString(file, sbom.generate(Path.of("/work/app"), Set.of()).content());

    final Result result = validate(file);

    assertThat(result.exitCode()).as(result.output()).isZero();
  }

  private static LicenseService mixedEcosystems() {
    return LicenseFixtures.service(
        LicenseFixtures.inventory(
            LicenseFixtures.dependency(
                Ecosystem.NPM, "@scope/pkg", "1.0.0", DependencyScope.RUNTIME, RUNTIME),
            LicenseFixtures.dependency(
                Ecosystem.MAVEN, "com.example:lib", "1.0", DependencyScope.RUNTIME, ""),
            LicenseFixtures.dependency(
                Ecosystem.PYPI, "requests", "2.32.0", DependencyScope.TEST, ""),
            LicenseFixtures.dependency(
                Ecosystem.SWIFT_URL,
                "https://github.com/apple/swift-log",
                "1.5.0",
                DependencyScope.RUNTIME,
                ""),
            LicenseFixtures.dependency(
                Ecosystem.NPM, "mystery", "2.0.0", DependencyScope.RUNTIME, RUNTIME)),
        Map.of(
            "@scope/pkg", LicenseFixtures.declared("Apache-2.0"),
            "com.example:lib",
                LicenseFixtures.declared("GPL-2.0-only WITH Classpath-exception-2.0"),
            "requests", LicenseFixtures.declared("MIT OR GPL-3.0-only"),
            "https://github.com/apple/swift-log",
                LicenseFixtures.declared("Eclipse Public License 2.0"),
            "mystery", LicenseLookup.missing(LicenseLookup.Reason.NO_LICENSE_DECLARED, "none")));
  }

  private Result validate(final Path file) throws IOException, InterruptedException {
    final Path validator = work.resolve("ValidateSbom.src");
    try (InputStream in = SbomSchemaSmokeTest.class.getResourceAsStream("/sbom/ValidateSbom.src")) {
      Files.write(validator, in.readAllBytes());
    }
    final Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    final Process process =
        new ProcessBuilder(
                java.toString(),
                "--source",
                "25",
                "-cp",
                System.getProperty("ward.sbom.schema"),
                validator.toString(),
                file.toString())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(MINUTE, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), output);
  }

  private record Result(int exitCode, String output) {}
}
