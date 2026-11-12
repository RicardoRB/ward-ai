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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a built executable's {@code ward} gate over a small project in every engine language, and
 * its {@code build-policy} check over the POM.
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
              <artifactId>shop</artifactId><version>1</version>
              <dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId>
              <version>[1.0,2.0)</version></dependency></dependencies></project>
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

  private static final String WARD_TOML =
      """
      version = 2

      [checks.ward]
      uses = "ward"
      rules = [%s]

      [[checks.ward.layers]]
      from = ["domain"]
      forbidden = ["infrastructure"]

      [checks.ward.cycles]
      roots = ["src/main/java/shop"]

      [checks.build-policy]
      uses = "build-policy"
      """;

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
        .contains("ward:build/no-dynamic-version");
    assertThat(warm.exitCode()).isEqualTo(FAILED);
    assertThat(warm.stdout()).isEqualTo(cold.stdout());
  }

  private void writeProject() throws IOException {
    for (final Map.Entry<String, String> source : SOURCES.entrySet()) {
      final Path file = project.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
    }
    final String rules = String.join(", ", RULES.stream().map(id -> "\"" + id + "\"").toList());
    Files.writeString(project.resolve("ward.toml"), WARD_TOML.formatted(rules));
  }

  private Run check(final Path binary) throws IOException, InterruptedException {
    final Path stdout = Files.createTempFile("ward-out", ".json");
    final Path stderr = Files.createTempFile("ward-err", ".txt");
    final Process process =
        new ProcessBuilder(binary.toString(), "check", "--format", "json", "-C", project.toString())
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
