package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compiles fixtures with the {@code parity/ParityJavac.src} launcher, which runs javac in one JVM
 * with Error Prone and NullAway on the class path.
 */
final class ParityJavac {

  private static final List<String> EXPORTS =
      Stream.of(
              "api",
              "file",
              "main",
              "model",
              "parser",
              "processing",
              "tree",
              "util",
              "code",
              "comp")
          .map(name -> "--add-exports=jdk.compiler/com.sun.tools.javac." + name + "=ALL-UNNAMED")
          .toList();
  private static final List<String> OPENS =
      Stream.of("code", "comp")
          .map(name -> "--add-opens=jdk.compiler/com.sun.tools.javac." + name + "=ALL-UNNAMED")
          .toList();
  private static final String LAUNCHER = "ParityJavac";
  private static final int DIAGNOSTIC_FIELDS = 6;
  private static final int LINE_FIELD = 3;
  private static final int COLUMN_FIELD = 4;

  /** A diagnostic of the compiler. */
  record Diagnostic(String fixture, String kind, int line, int column, String message) {}

  /** What a compile run reported. */
  record Result(List<String> failed, List<Diagnostic> diagnostics) {}

  private ParityJavac() {}

  /**
   * Compiles the fixtures and returns the ones that did not compile and all diagnostics.
   *
   * @param mode {@code ep}, {@code nullaway} or {@code classes}
   * @param fixtures the fixtures to compile, such as {@code my-rule/violating}
   * @param checks the Error Prone check names to enable
   * @param classes the folder that receives class files in {@code classes} mode
   */
  static Result compile(
      final ParityEnvironment environment,
      final String mode,
      final List<String> fixtures,
      final List<String> checks,
      final Path classes)
      throws IOException, InterruptedException {
    final Path folder = environment.workFolder("javac-" + mode);
    final String classpath = environment.classpath("errorprone");
    final Path launcher = folder.resolve("launcher");
    Files.createDirectories(launcher);
    try (InputStream source = ParityJavac.class.getResourceAsStream("/parity/ParityJavac.src")) {
      Files.write(launcher.resolve("ParityJavac.java"), source.readAllBytes());
    }
    ParityEnvironment.run(
        List.of(
            ParityEnvironment.javac(),
            "-cp",
            classpath,
            "-d",
            launcher.toString(),
            launcher.resolve("ParityJavac.java").toString()),
        folder.resolve("javac.out"),
        folder.resolve("javac.err"));
    final List<String> entries = new ArrayList<>();
    for (final String fixture : fixtures) {
      entries.add(fixture + "\t" + environment.fixtures().resolve(fixture + ".java"));
    }
    Files.write(folder.resolve("list.txt"), entries, StandardCharsets.UTF_8);
    Files.write(folder.resolve("checks.txt"), checks, StandardCharsets.UTF_8);
    final List<String> arguments = new ArrayList<>(EXPORTS);
    arguments.addAll(OPENS);
    Files.write(folder.resolve("jvm.args"), arguments, StandardCharsets.UTF_8);
    final Path output = folder.resolve("output.txt");
    ParityEnvironment.run(
        List.of(
            ParityEnvironment.java(),
            "@" + folder.resolve("jvm.args"),
            "-cp",
            launcher + Path.of(":").toString() + classpath,
            LAUNCHER,
            mode,
            folder.resolve("list.txt").toString(),
            classes.toString(),
            folder.resolve("checks.txt").toString()),
        output,
        folder.resolve("run.err"));
    return read(output);
  }

  private static Result read(final Path output) throws IOException {
    final List<String> failed = new ArrayList<>();
    final List<Diagnostic> diagnostics = new ArrayList<>();
    for (final String line : Files.readAllLines(output, StandardCharsets.UTF_8)) {
      final String[] parts = line.split("\t", DIAGNOSTIC_FIELDS);
      if ("C".equals(parts[0]) && "error".equals(parts[2])) {
        failed.add(parts[1]);
      } else if ("D".equals(parts[0]) && parts.length == DIAGNOSTIC_FIELDS) {
        diagnostics.add(
            new Diagnostic(
                parts[1],
                parts[2],
                Integer.parseInt(parts[LINE_FIELD].trim()),
                Integer.parseInt(parts[COLUMN_FIELD].trim()),
                parts[DIAGNOSTIC_FIELDS - 1]));
      }
    }
    return new Result(failed, diagnostics);
  }
}
