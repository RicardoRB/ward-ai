package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.gradle.GradleToolConfigurator;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that {@code --architecture-engine tool} writes the same files as before the ward engine
 * owned cycles and independence: every generated file's path and SHA-256 must match the golden
 * manifest. Run with {@code -Dward.golden.write=true} only to record a new baseline.
 */
class ToolEngineInitGoldenTest {

  private static final String GOLDEN_DIR = "src/test/resources/golden/init-tool/";
  private static final String MAVEN_STRICT = "maven-strict-hexagonal.txt";
  private static final String MAVEN_STANDARD = "maven-standard-layered.txt";
  private static final Pattern LIMITS = Pattern.compile("<limits>(.*?)</limits>");

  @TempDir Path root;

  @Test
  void strictMavenToolEngineOutputIsByteIdentical() throws IOException {
    mavenProject();

    final int exit =
        init()
            .run(
                "",
                "--preset",
                "strict",
                "--integration",
                "none",
                "--architecture",
                "hexagonal",
                "--architecture-engine",
                "tool",
                "--gate",
                "archunit",
                "--yes");

    assertGolden(MAVEN_STRICT, exit);
  }

  @Test
  void standardMavenToolEngineOutputIsByteIdentical() throws IOException {
    mavenProject();

    final int exit =
        init()
            .run(
                "",
                "--preset",
                "standard",
                "--integration",
                "none",
                "--architecture",
                "layered",
                "--architecture-engine",
                "tool",
                "--yes");

    assertGolden(MAVEN_STANDARD, exit);
  }

  private void assertGolden(final String name, final int exit) throws IOException {
    final Path golden = Path.of(GOLDEN_DIR, name);
    final String actual = manifest();
    assertThat(exit).isEqualTo(ExitCodes.OK);
    if (Boolean.getBoolean("ward.golden.write")) {
      Files.createDirectories(golden.getParent());
      Files.writeString(golden, actual);
    }
    assertThat(actual).isEqualTo(Files.readString(golden));
  }

  /** One line per file the run left in the project: its relative path and SHA-256. */
  private String manifest() throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> !file.startsWith(root.resolve(".git")))
          .sorted()
          .map(file -> root.relativize(file) + " " + sha256(file) + "\n")
          .collect(Collectors.joining());
    }
  }

  private static String sha256(final Path file) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes(file)));
    } catch (final IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("could not hash " + file, exception);
    }
  }

  /**
   * The file's bytes. shortcut: the JaCoCo limits of the pom come from an unordered set, so their
   * order changes between JVM runs; their order is sorted here, and the limits themselves are kept.
   */
  private static byte[] bytes(final Path file) throws IOException {
    final String text = Files.readString(file, StandardCharsets.UTF_8);
    if (!"pom.xml".equals(String.valueOf(file.getFileName()))) {
      return text.getBytes(StandardCharsets.UTF_8);
    }
    final Matcher limits = LIMITS.matcher(text);
    final StringBuilder sorted = new StringBuilder();
    while (limits.find()) {
      final List<String> items = new ArrayList<>(List.of(limits.group(1).split("(?=<limit>)")));
      items.removeIf(String::isEmpty);
      items.sort(null);
      limits.appendReplacement(sorted, Matcher.quoteReplacement("<limits>" + String.join("", items) + "</limits>"));
    }
    limits.appendTail(sorted);
    return sorted.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void mavenProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>g</groupId>"
            + "<artifactId>a</artifactId><version>1</version></project>\n");
    Files.createDirectories(root.resolve("src/main/java/shop/domain"));
    Files.createDirectories(root.resolve("src/main/java/shop/adapter"));
    Files.writeString(
        root.resolve("src/main/java/shop/App.java"),
        "package shop;\nclass App {}\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        root.resolve("src/main/java/shop/domain/Order.java"),
        "package shop.domain;\nclass Order {}\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        root.resolve("src/main/java/shop/adapter/Db.java"),
        "package shop.adapter;\nclass Db {}\n",
        StandardCharsets.UTF_8);
  }

  private InitCommandFixture init() throws IOException {
    return InitCommandFixture.repository(
        root,
        new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));
  }
}
