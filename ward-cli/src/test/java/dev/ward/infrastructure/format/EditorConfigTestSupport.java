package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptionType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

/** Shared fixtures for the EditorConfig configuration tests: a temporary project and helpers. */
class EditorConfigTestSupport {

  /** A Java file name used across the tests. */
  protected static final String JAVA_FILE = "A.java";

  /** Shared options as a Java-like formatter declares them: width 100, indent 4, one blank line. */
  protected static final FormatOption LINE_WIDTH_OPTION =
      new FormatOption("line-width", FormatOptionType.INTEGER, "100", List.of());

  protected static final FormatOption INDENT_STYLE_OPTION =
      new FormatOption("indent-style", FormatOptionType.TEXT, "space", List.of("space", "tab"));
  protected static final FormatOption MAX_BLANK_LINES_OPTION =
      new FormatOption("max-blank-lines", FormatOptionType.INTEGER, "1", List.of());
  protected static final FormatOption JAVA_INDENT_OPTION =
      new FormatOption("indent-width", FormatOptionType.INTEGER, "4", List.of());
  protected static final FormatOption TYPESCRIPT_INDENT_OPTION =
      new FormatOption("indent-width", FormatOptionType.INTEGER, "2", List.of());
  protected static final int DEFAULT_LINE_WIDTH = 100;
  protected static final int DEFAULT_MAX_BLANK_LINES = 1;

  protected static final FormatOption CONTINUATION =
      new FormatOption("continuation-indent-size", FormatOptionType.INTEGER, "8", List.of());
  protected static final List<FormatOption> JAVA_OPTIONS =
      List.of(
          LINE_WIDTH_OPTION,
          INDENT_STYLE_OPTION,
          MAX_BLANK_LINES_OPTION,
          JAVA_INDENT_OPTION,
          CONTINUATION);
  protected static final List<FormatOption> TYPESCRIPT_OPTIONS =
      List.of(
          LINE_WIDTH_OPTION, INDENT_STYLE_OPTION, MAX_BLANK_LINES_OPTION, TYPESCRIPT_INDENT_OPTION);

  protected static final int JAVA_INDENT = 4;
  protected static final int TYPESCRIPT_INDENT = 2;
  protected static final int PYTHON_WIDTH = 88;
  protected static final int WIDE_WIDTH = 120;
  protected static final int NARROW_WIDTH = 60;
  protected static final int SHORT_INDENT = 3;
  protected static final int MEDIUM_INDENT = 6;
  protected static final int PARENT_INDENT = 8;
  protected static final int TAB_WIDTH = 6;
  protected static final int BLANK_LINES = 2;
  protected static final int CONTINUATION_VALUE = 12;
  protected static final byte INVALID_LEAD_BYTE = (byte) 0xC3;
  protected static final byte INVALID_TRAIL_BYTE = 0x28;

  @TempDir private Path project;

  protected Path project() {
    return project;
  }

  protected FormatSettings resolve(
      final String relativePath,
      final AnalysisLanguage language,
      final List<FormatOption> declared) {
    return new EditorConfigFormatConfiguration(project).resolve(relativePath, language, declared);
  }

  protected void write(final String text) throws IOException {
    write(".editorconfig", text);
  }

  protected void write(final String relative, final String text) throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  /** The declared options of a formatter, with extra language options appended. */
  protected static List<FormatOption> with(
      final List<FormatOption> base, final FormatOption... extra) {
    final List<FormatOption> all = new java.util.ArrayList<>(base);
    all.addAll(List.of(extra));
    return List.copyOf(all);
  }
}
