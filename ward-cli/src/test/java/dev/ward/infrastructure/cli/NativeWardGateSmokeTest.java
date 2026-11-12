package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a built executable's {@code ward} gate over a small project in every engine language.
 *
 * <p>The JVM tests cannot see what the native image leaves out: grammars, rule and taint resources,
 * reflective codecs. This test runs the real binary twice, so the second run also reads the
 * analysis cache. Run it with {@code ./mvnw -Dtest=NativeWardGateSmokeTest
 * -Dward.native.binary=target/ward test} after {@code native:compile}.
 */
@EnabledIfSystemProperty(named = "ward.native.binary", matches = ".+")
class NativeWardGateSmokeTest {
  private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
  private static final int FAILED = 1;

  private static final Map<String, String> SOURCES =
      Map.ofEntries(
          Map.entry(
              "pom.xml",
              """
              <project><modelVersion>4.0.0</modelVersion><groupId>shop</groupId>
              <artifactId>shop</artifactId><version>1</version></project>
              """),
          Map.entry("package.json", "{\"name\": \"shop\"}\n"),
          Map.entry(
              "src/main/java/shop/domain/Order.java",
              """
              package shop.domain;

              import java.util.*;
              import shop.infrastructure.Store;

              public class Order {
                List<Store> stores;
              }
              """),
          Map.entry(
              "src/main/java/shop/infrastructure/Store.java",
              """
              package shop.infrastructure;

              import javax.servlet.http.HttpServletRequest;
              import shop.domain.Order;

              public class Store {
                Order order;

                void run(HttpServletRequest request) throws Exception {
                  String cmd = request.getParameter("cmd");
                  Runtime.getRuntime().exec(cmd);
                }
              }
              """),
          Map.entry("web/app.ts", "export const user_name = 1;\n"),
          Map.entry("web/legacy.js", "const user_name = 1;\nmodule.exports = user_name;\n"),
          Map.entry("tools/report.py", "def BadName():\n    return 1\n"),
          Map.entry("Sources/Shop/Shape.swift", "struct bad_shape {}\n"),
          Map.entry("app/Main.kt", "fun Bad_name() {}\n"));

  private static final List<String> RULES =
      List.of(
          "ward:java/star-import",
          "ward:java/command-injection",
          "ward:typescript/naming",
          "ward:javascript/naming",
          "ward:python/naming",
          "ward:swift/naming",
          "ward:kotlin/function-name");

  // Built from two literals, so that no source file holds a token.
  private static final String TOKEN = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";

  private static final String WARD_TOML =
      """
      version = 2

      [checks.secrets]
      uses = "secrets"

      [checks.ward]
      uses = "ward"
      rules = [%s]

      [[checks.ward.layers]]
      from = ["domain"]
      forbidden = ["infrastructure"]

      [checks.ward.cycles]
      roots = ["src/main/java/shop"]

      [checks.coverage]
      uses = "coverage"
      minimum = 90
      minimum-branches = 50
      report = "coverage/lcov.info"
      """;

  /** The lcov report of the coverage check: half the lines and branches of one file covered. */
  private static final String LCOV =
      "TN:\nSF:web/app.ts\nDA:1,1\nDA:2,0\nBRDA:1,0,0,1\nBRDA:1,0,1,0\nBRF:2\nBRH:1\n"
          + "end_of_record\n";

  @TempDir Path project;

  @Test
  void wardGateReportsEveryLanguageAndRepeatsItsOutputFromTheCache() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    assertThat(binary).as("ward.native.binary; run native:compile first").isExecutable();
    writeProject();

    final Run cold = check(binary);
    final Run warm = check(binary);

    assertThat(cold.stderr())
        .doesNotContain("Exception")
        .doesNotContain("unexpected error")
        .doesNotContain("not run");
    assertThat(cold.exitCode()).as(cold.stderr()).isEqualTo(FAILED);
    assertThat(cold.stdout())
        .contains(RULES)
        .contains("ward:architecture/layers")
        .contains("ward:architecture/cycles")
        .contains("ward:secrets/github-pat")
        .doesNotContain(TOKEN)
        .contains("Line coverage 50% is below the minimum of 90%")
        .contains("Branch coverage 50% meets the minimum of 50%");
    assertThat(warm.exitCode()).isEqualTo(FAILED);
    assertThat(warm.stdout()).isEqualTo(cold.stdout());
  }

  @Test
  void licenseGateSbomAndLicensesRunFromTheNativeImage() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    write(
        "package.json",
        "{\"name\":\"shop\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"1.3.0\"}}");
    write(
        "package-lock.json",
        "{\"name\":\"shop\",\"version\":\"1.0.0\",\"lockfileVersion\":3,\"requires\":true,"
            + "\"packages\":{\"\":{\"name\":\"shop\",\"version\":\"1.0.0\","
            + "\"dependencies\":{\"left-pad\":\"1.3.0\"}},"
            + "\"node_modules/left-pad\":{\"version\":\"1.3.0\"}}}");
    write(
        "node_modules/left-pad/package.json",
        "{\"name\":\"left-pad\",\"version\":\"1.3.0\",\"license\":\"MIT\"}");
    write("ward.toml", "version = 2\n\n[checks.license]\nuses = \"license\"\ndeny = [\"MIT\"]\n");

    final Run gate = run(binary, "check", "--format", "json", "-C", project.toString());
    final Run sbom = run(binary, "sbom", "--directory", project.toString());
    final Run notice = run(binary, "licenses", "--directory", project.toString());

    assertThat(gate.exitCode()).as(gate.stderr()).isEqualTo(FAILED);
    assertThat(gate.stdout()).contains("ward:license/denied");
    assertThat(sbom.exitCode()).as(sbom.stderr()).isZero();
    assertThat(sbom.stdout()).contains("CycloneDX").contains("pkg:npm/left-pad@1.3.0");
    assertThat(notice.exitCode()).as(notice.stderr()).isZero();
    assertThat(notice.stdout()).contains("left-pad").contains("MIT");
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = project.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private void writeProject() throws IOException {
    for (final Map.Entry<String, String> source : SOURCES.entrySet()) {
      final Path file = project.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
    }
    Files.createDirectories(project.resolve("config"));
    Files.writeString(project.resolve("config/app.yml"), "token: " + TOKEN + "\n");
    final String rules = String.join(", ", RULES.stream().map(id -> "\"" + id + "\"").toList());
    Files.writeString(project.resolve("ward.toml"), WARD_TOML.formatted(rules));
    // Written last, so that no source file it names is newer than the report.
    Files.createDirectories(project.resolve("coverage"));
    Files.writeString(project.resolve("coverage/lcov.info"), LCOV);
  }

  private Run check(final Path binary) throws IOException, InterruptedException {
    return run(binary, "check", "--format", "json", "-C", project.toString());
  }

  private Run run(final Path binary, final String... arguments)
      throws IOException, InterruptedException {
    final Path stdout = Files.createTempFile("ward-out", ".json");
    final Path stderr = Files.createTempFile("ward-err", ".txt");
    final Process process =
        new ProcessBuilder(
                Stream.concat(Stream.of(binary.toString()), Stream.of(arguments)).toList())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
    try {
      assertThat(process.waitFor(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      return new Run(
          process.exitValue(),
          Files.readString(stdout, StandardCharsets.UTF_8),
          Files.readString(stderr, StandardCharsets.UTF_8));
    } finally {
      process.destroyForcibly();
    }
  }

  private record Run(int exitCode, String stdout, String stderr) {}
}
