package dev.ward.infrastructure.report;

import dev.ward.domain.gate.ReportSink;
import dev.ward.domain.gate.ReportWriteException;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;

/** Writes check reports to the local file system as UTF-8. */
public final class FileReportSink implements ReportSink {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileReportSink() {}

  @Override
  public void persist(final Path target, final String content) {
    final Path absolute = target.toAbsolutePath().normalize();
    try {
      final Path directory = absolute.resolveSibling(""); // the directory that holds the target
      Files.createDirectories(directory);
      final Path temporary = Files.createTempFile(directory, LockFiles.fileName(absolute), ".tmp");
      Files.writeString(temporary, content, StandardCharsets.UTF_8);
      Files.move(
          temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new ReportWriteException("Cannot write " + absolute.getFileName(), exception);
    }
  }
}
