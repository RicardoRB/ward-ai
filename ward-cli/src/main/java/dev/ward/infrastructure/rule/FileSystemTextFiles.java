package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.application.gate.TextRead;
import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandExecutor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the text files that the {@code secrets} check scans. Unlike the source-unit reader it reads
 * hidden folders, because {@code .github} and {@code .aws} hold secrets, and Java sources under a
 * resource folder. Build output, {@code .git}, {@code .ward} and files that Git ignores are
 * skipped, and so is a file with a NUL byte in its first 8 KiB, which is not text.
 */
final class FileSystemTextFiles {
  private static final Logger LOGGER = Logger.getLogger(FileSystemTextFiles.class.getName());

  /** The largest text file that is scanned. */
  static final long MAX_BYTES = 8L * 1024 * 1024;

  private static final int BINARY_PROBE = 8 * 1024;
  private static final Set<String> SKIPPED =
      Set.of("target", "build", "dist", "node_modules", "mutants", ".git", ".ward");

  private FileSystemTextFiles() {}

  /** Reads the files as {@link dev.ward.application.gate.AnalysisSourceUnits#readText} says. */
  static TextRead read(
      final Path root,
      final List<String> paths,
      final Predicate<String> excluded,
      final CommandExecutor executor) {
    final Set<String> ignored = GitIgnoredFiles.of(root, executor);
    final List<TextFile> files = new ArrayList<>();
    final List<RuleExecutionError> errors = new ArrayList<>();
    final Reader reader = new Reader(root, ignored, excluded, files, errors);
    try {
      if (paths.isEmpty()) {
        Files.walkFileTree(root, reader);
      } else {
        paths.forEach(path -> reader.edited(root.resolve(path)));
      }
    } catch (final IOException exception) {
      throw new WardException("Could not list the text files under " + root + ".", exception);
    }
    files.sort(Comparator.comparing(TextFile::path));
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine(
          "event=secrets-read files="
              + files.size()
              + " skipped="
              + reader.skipped
              + " binary="
              + reader.binary
              + " errors="
              + errors.size());
    }
    return new TextRead(files, errors);
  }

  /** Collects the text files of a walk, or of the edited paths. */
  private static final class Reader extends SimpleFileVisitor<Path> {
    private final Path root;
    private final Set<String> ignored;
    private final Predicate<String> excluded;
    private final List<TextFile> files;
    private final List<RuleExecutionError> errors;
    private int skipped;
    private int binary;

    Reader(
        final Path root,
        final Set<String> ignored,
        final Predicate<String> excluded,
        final List<TextFile> files,
        final List<RuleExecutionError> errors) {
      super();
      this.root = root;
      this.ignored = ignored;
      this.excluded = excluded;
      this.files = files;
      this.errors = errors;
    }

    @Override
    public FileVisitResult preVisitDirectory(
        final Path directory, final BasicFileAttributes attributes) {
      if (directory.equals(root)) {
        return FileVisitResult.CONTINUE;
      }
      final boolean skip =
          SKIPPED.contains(nameOf(directory)) || ignored.contains(relative(directory) + "/");
      skipped += skip ? 1 : 0;
      return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
      if (attributes.isRegularFile()) {
        add(file, attributes.size());
      }
      return FileVisitResult.CONTINUE;
    }

    /** An edited path is read when it still exists and no skipped folder is above it. */
    void edited(final Path file) {
      final String path = relative(file);
      final boolean inSkipped =
          List.of(path.split("/")).subList(0, Math.max(0, path.split("/").length - 1)).stream()
              .anyMatch(SKIPPED::contains);
      if (Files.isRegularFile(file) && !inSkipped && !GitIgnoredFiles.covers(ignored, path)) {
        try {
          add(file, Files.size(file));
        } catch (final IOException exception) {
          errors.add(new RuleExecutionError(null, path, "the file could not be read"));
        }
      }
    }

    private void add(final Path file, final long size) {
      final String path = relative(file);
      if (ignored.contains(path) || excluded.test(path)) {
        skipped++;
        return;
      }
      if (size > MAX_BYTES) {
        errors.add(new RuleExecutionError(null, path, "the text file is larger than 8 MiB"));
        return;
      }
      try {
        final byte[] bytes = Files.readAllBytes(file);
        if (isBinary(bytes)) {
          binary++;
        } else {
          files.add(new TextFile(path, decode(bytes)));
        }
      } catch (final IOException exception) {
        errors.add(new RuleExecutionError(null, path, "the file could not be read"));
      }
    }

    private String relative(final Path file) {
      return root.relativize(file).toString().replace('\\', '/');
    }
  }

  /** Returns the last name of {@code path}, or an empty string for a root with no name. */
  private static String nameOf(final Path path) {
    final Path name = path.getFileName();
    return name == null ? "" : name.toString();
  }

  private static boolean isBinary(final byte[] bytes) {
    final int probe = Math.min(bytes.length, BINARY_PROBE);
    for (int index = 0; index < probe; index++) {
      if (bytes[index] == 0) {
        return true;
      }
    }
    return false;
  }

  /** UTF-8, or ISO-8859-1 when the bytes are not valid UTF-8, so a Latin-1 file is scanned. */
  private static String decode(final byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (final CharacterCodingException exception) {
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  }
}
