package dev.ward.infrastructure.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The offline Kotlin module fixture, copied into a temporary root for each test. */
public final class ModuleFixtures {
  static final Path FIXTURE = Path.of("src/test/resources/fixtures/gradle-kotlin-modules");

  private ModuleFixtures() {}

  /** Copies the fixture into {@code root} and returns the Kotlin Gradle project there. */
  public static Project copy(final Path root) throws IOException {
    try (Stream<Path> files = Files.walk(FIXTURE)) {
      for (final Path source : files.toList()) {
        final Path target = root.resolve(FIXTURE.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target);
        }
      }
    }
    return new Project(root, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE);
  }

  /** Reads the file {@code name} under {@code root}. */
  public static String read(final Path root, final String name) throws IOException {
    return Files.readString(root.resolve(name));
  }

  /** Returns one gate option per id, each run through the Gradle wrapper. */
  public static List<InitGateOption> gates(final String... ids) {
    return Stream.of(ids)
        .map(id -> new InitGateOption(id, id, List.of("./gradlew", id), true, "available"))
        .toList();
  }
}
