package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkLocatorTest {
  private static final String LINUX = "Linux";
  private static final String MAC = "Mac OS X";
  private static final String BIN = "bin";

  @TempDir Path temp;

  private Path jdk(final String name) throws IOException {
    final Path home = temp.toRealPath().resolve(name);
    Files.createDirectories(home.resolve("lib"));
    Files.createDirectories(home.resolve(BIN));
    Files.writeString(home.resolve("lib").resolve("modules"), "image");
    Files.writeString(home.resolve(BIN).resolve("java"), "binary");
    return home;
  }

  private static JdkLocator locator(
      final Map<String,
      String> environment,
      final FakeCommandExecutor executor,
      final String os
  ) {
    return new JdkLocator(executor, environment::get, Optional.empty(), os);
  }

  @Test
  @DisplayName("JDK found without `JAVA_HOME`")
  void jdkFoundWithoutJavaHome() throws IOException {
    // Given a Java project and a JDK on PATH, and JAVA_HOME unset
    final Path home = jdk("on-path");
    final Map<String, String> environment =
        Map.of("PATH", temp.resolve("missing") + java.io.File.pathSeparator + home.resolve(BIN));
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");

    // When Ward looks for the JDK
    final Optional<Path> found = locator(environment, executor, LINUX).find();

    // Then it is the JDK that a run with JAVA_HOME set would read
    assertThat(found).contains(home);
    assertThat(executor.commands).isEmpty();
  }

  @Test
  @DisplayName("no JDK at all")
  void noJdkAtAll() throws IOException {
    // Given a Java project and no JDK anywhere Ward looks
    final Path stub = temp.resolve("stub");
    Files.createDirectories(stub.resolve(BIN));
    Files.writeString(stub.resolve(BIN).resolve("java"), "no image beside it");
    final Map<String, String> environment =
        Map.of("JAVA_HOME", temp.resolve("none").toString(), "PATH", stub.resolve(BIN).toString());
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "");

    // When Ward looks for the JDK
    final Optional<Path> found = locator(environment, executor, MAC).find();

    // Then none is found, so the JDK type rules are not run (see NativeTypeFactsBindingsTest)
    assertThat(found).isEmpty();
  }

  @Test
  void javaHomeComesBeforeThePath() throws IOException {
    final Path home = jdk("home");
    final Path onPath = jdk("on-path");
    final Map<String, String> environment =
        Map.of("JAVA_HOME", home.toString(), "PATH", onPath.resolve(BIN).toString());

    assertThat(locator(environment, new FakeCommandExecutor(1, "", ""), LINUX).find())
        .contains(home);
  }

  @Test
  void aSymlinkedJavaOnThePathIsFollowedToItsJdk() throws IOException {
    final Path home = jdk("real");
    final Path bin = Files.createDirectories(temp.resolve("usr-bin"));
    Files.createSymbolicLink(bin.resolve("java"), home.resolve(BIN).resolve("java"));

    assertThat(
        locator(Map.of("PATH", bin.toString()), new FakeCommandExecutor(1, "", ""), LINUX).find()
    )
        .contains(home);
  }

  @Test
  void macOsAsksJavaHomeWhenThePathHasNoJdk() throws IOException {
    final Path home = jdk("mac");
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(new CommandResult(0, home + "\n", "", Duration.ZERO, null));

    assertThat(locator(Map.of(), executor, MAC).find()).contains(home);
    assertThat(executor.commands).containsExactly(java.util.List.of("/usr/libexec/java_home"));
  }

  @Test
  void otherSystemsDoNotAskJavaHome() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "/x", "");

    assertThat(locator(Map.of(), executor, LINUX).find()).isEmpty();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void aFailingJavaHomeCommandFindsNothing() {
    assertThat(locator(Map.of(), new FakeCommandExecutor(1, "", "no JDK"), MAC).find()).isEmpty();
  }

  @Test
  void theRunningJdkIsTheLastResort() throws IOException {
    final Path home = jdk("running");

    assertThat(
        new JdkLocator(
            new FakeCommandExecutor(1, "", ""),
            Map.<String,
            String> of()::get,
            Optional.of(home.toString()),
            LINUX
        ).find()
    )
        .contains(home);
  }
}
