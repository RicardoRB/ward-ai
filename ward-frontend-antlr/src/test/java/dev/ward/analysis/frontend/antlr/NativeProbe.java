package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Developer probe for the native image: parses the valid fixture of each language from a directory
 * and exits non-zero unless every parse succeeds with no diagnostics. Not shipped.
 */
public final class NativeProbe {

  private static final int EXIT_OK = 0;
  private static final int EXIT_FAILED = 1;
  private static final int EXIT_USAGE = 2;
  private static final int EXPECTED_ARGS = 1;

  /** The valid fixture of each language, by file name. */
  private static final List<Fixture> FIXTURES =
      List.of(
          new Fixture(AnalysisLanguage.JAVA, "java-valid.java", "compilationunit"),
          new Fixture(AnalysisLanguage.KOTLIN, "kotlin-valid.kt", "kotlinFile"),
          new Fixture(AnalysisLanguage.TYPESCRIPT, "typescript-valid.ts", "program"),
          new Fixture(AnalysisLanguage.JAVASCRIPT, "javascript-valid.js", "program"),
          new Fixture(AnalysisLanguage.PYTHON, "python-valid.py", "file_input"),
          new Fixture(AnalysisLanguage.SWIFT, "swift-valid.swift", "top_level"));

  private NativeProbe() {}

  /**
   * Runs the probe.
   *
   * @param args one argument: the directory that holds the fixture files
   */
  public static void main(final String[] args) {
    if (args.length != EXPECTED_ARGS) {
      usage(System.err);
      System.exit(EXIT_USAGE);
    }
    System.exit(run(Path.of(args[0]), System.out));
  }

  private static void usage(final PrintStream out) {
    out.println("usage: NativeProbe <fixtures-dir>");
  }

  static int run(final Path directory, final PrintStream out) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    int failures = 0;
    for (final Fixture fixture : FIXTURES) {
      if (!check(frontend, directory, fixture, out)) {
        failures++;
      }
    }
    out.println(failures == 0 ? "all parses succeeded" : failures + " parse(s) failed");
    return failures == 0 ? EXIT_OK : EXIT_FAILED;
  }

  private static boolean check(
      final AntlrLanguageFrontend frontend,
      final Path directory,
      final Fixture fixture,
      final PrintStream out) {
    try {
      final String text =
          Files.readString(directory.resolve(fixture.name()), StandardCharsets.UTF_8);
      final ParseResult result =
          frontend.parse(new SourceUnit(fixture.name(), fixture.language(), text));
      final boolean ok =
          fixture.rootKind().equals(result.tree().kind()) && result.diagnostics().isEmpty();
      out.println((ok ? "ok    " : "FAIL  ") + fixture.language() + " " + fixture.name());
      return ok;
    } catch (final IOException e) {
      out.println("FAIL  " + fixture.language() + " " + fixture.name() + " unreadable");
      return false;
    }
  }

  private record Fixture(AnalysisLanguage language, String name, String rootKind) {}
}
