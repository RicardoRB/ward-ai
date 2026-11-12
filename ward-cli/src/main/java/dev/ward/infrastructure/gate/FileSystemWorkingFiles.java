package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.WorkingFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Lists and reads working-tree files from the local file system. */
public final class FileSystemWorkingFiles implements WorkingFiles {
  private static final Logger LOGGER = Logger.getLogger(FileSystemWorkingFiles.class.getName());

  /** Creates the adapter. */
  @Inject
  public FileSystemWorkingFiles() {}

  @Override
  public List<String> files(final Path root, final String directory) {
    final Path base = root.resolve(directory);
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(base)) {
      return walk.filter(Files::isRegularFile)
          .map(file -> base.relativize(file).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException exception) {
      LOGGER.log(
          Level.WARNING,
          () -> "event=working-files-list-failed error=" + exception.getClass().getSimpleName());
      return List.of();
    }
  }

  @Override
  public Optional<String> read(final Path root, final String path) {
    try {
      return Optional.of(Files.readString(root.resolve(path), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      return Optional.empty();
    }
  }
}
