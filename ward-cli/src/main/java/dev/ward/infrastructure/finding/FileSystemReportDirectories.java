package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportWriteException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Creates report parent directories and clears stale reports inside the project root. */
public final class FileSystemReportDirectories implements ReportDirectories {
  private static final Logger LOGGER =
      Logger.getLogger(FileSystemReportDirectories.class.getName());

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemReportDirectories() {}

  @Override
  public void prepareParent(final Path projectRoot, final String reportPath) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path parent;
    try {
      parent = root.resolve(reportPath).normalize().getParent();
    } catch (InvalidPathException exception) {
      LOGGER.log(Level.INFO, "event=report-directory-skipped reason=invalid-path");
      return;
    }
    if (parent == null || !parent.startsWith(root) || parent.equals(root)) {
      LOGGER.log(Level.INFO, "event=report-directory-skipped reason=outside-project");
      return;
    }
    if (Files.isDirectory(parent)) {
      return;
    }
    try {
      Files.createDirectories(parent);
      LOGGER.log(Level.INFO, () -> "event=report-directory-created dir=" + root.relativize(parent));
    } catch (IOException exception) {
      throw new ReportWriteException("Cannot create report directory", exception);
    }
  }

  @Override
  public void prepareFresh(final Path projectRoot, final String reportPath) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path report;
    try {
      report = root.resolve(reportPath).normalize();
    } catch (InvalidPathException exception) {
      LOGGER.log(Level.INFO, "event=report-clear-skipped reason=invalid-path");
      return;
    }
    if (!report.startsWith(root) || report.equals(root)) {
      LOGGER.log(Level.INFO, "event=report-clear-skipped reason=outside-project");
      return;
    }
    if (Files.exists(report, LinkOption.NOFOLLOW_LINKS)) {
      delete(report);
      LOGGER.log(Level.INFO, () -> "event=report-cleared path=" + root.relativize(report));
    }
    prepareParent(root, reportPath);
  }

  /**
   * Deletes {@code report} and, when it is a directory such as an Xcode result bundle, its tree.
   */
  private static void delete(final Path report) {
    try (Stream<Path> tree = Files.walk(report)) {
      final List<Path> deepestFirst = tree.sorted(Comparator.reverseOrder()).toList();
      for (final Path path : deepestFirst) {
        Files.delete(path);
      }
    } catch (IOException exception) {
      throw new ReportWriteException("Cannot remove stale report", exception);
    }
  }
}
