package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Compiles one side of an api-compat fixture with javac or kotlinc and packs the classes. */
final class JapicmpFixtureBuild {
  private static final String KOTLINC = "ward.kotlinc.binary";
  private static final long TIMEOUT_MINUTES = 5;

  private JapicmpFixtureBuild() {}

  /** Whether the property names a kotlinc executable. */
  static boolean kotlinConfigured() {
    return !System.getProperty(KOTLINC, "").isBlank();
  }

  /** The {@code kotlin-stdlib.jar} beside the configured kotlinc. */
  static Path stdlib() {
    return Path.of(System.getProperty(KOTLINC))
        .toAbsolutePath()
        .getParent()
        .getParent()
        .resolve("lib/kotlin-stdlib.jar");
  }

  /** Compiles the sources of {@code side} into {@code directory} and returns the jar. */
  static Path jar(final boolean kotlin, final Path side, final Path directory)
      throws IOException, InterruptedException {
    final Path classes = Files.createDirectories(directory.resolve("classes"));
    final String extension = kotlin ? ".kt" : ".java";
    final List<Path> sources;
    try (Stream<Path> walk = Files.walk(side)) {
      sources =
          walk.filter(path -> path.toString().endsWith(extension))
              .map(Path::toAbsolutePath)
              .sorted()
              .toList();
    }
    if (kotlin) {
      kotlinc(sources, classes, directory);
    } else {
      javac(sources, classes);
    }
    return pack(classes, directory.resolve("api.jar"));
  }

  private static void javac(final List<Path> sources, final Path classes) {
    final List<JavaFileObject> units =
        sources.stream().map(FixtureSource::new).map(JavaFileObject.class::cast).toList();
    assertThat(
            ToolProvider.getSystemJavaCompiler()
                .getTask(
                    null, null, null, List.of("-d", classes.toString(), "-nowarn"), null, units)
                .call())
        .isTrue();
  }

  private static void kotlinc(final List<Path> sources, final Path classes, final Path directory)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of(System.getProperty(KOTLINC)));
    sources.forEach(source -> command.add(source.toString()));
    command.addAll(List.of("-classpath", stdlib().toString(), "-d", classes.toString()));
    run(command, directory);
  }

  /** Runs {@code command} in {@code directory} and requires exit code zero. */
  static void run(final List<String> command, final Path directory)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).as(output).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
  }

  private static Path pack(final Path classes, final Path jar) throws IOException {
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out);
        Stream<Path> walk = Files.walk(classes)) {
      for (final Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
        add(jarOut, classes, file);
      }
    }
    return jar;
  }

  private static void add(final JarOutputStream jar, final Path classes, final Path file)
      throws IOException {
    jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
    Files.copy(file, jar);
    jar.closeEntry();
  }

  /** A fixture file; several public types share one file, so any file name is compatible. */
  private static final class FixtureSource extends SimpleJavaFileObject {
    private final Path path;

    FixtureSource(final Path path) {
      super(path.toUri(), Kind.SOURCE);
      this.path = path;
    }

    @Override
    public CharSequence getCharContent(final boolean ignoreEncodingErrors) throws IOException {
      return Files.readString(path);
    }

    @Override
    public boolean isNameCompatible(final String simpleName, final Kind kind) {
      return true;
    }
  }
}
