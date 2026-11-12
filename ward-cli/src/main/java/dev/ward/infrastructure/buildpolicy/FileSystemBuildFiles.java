package dev.ward.infrastructure.buildpolicy;

import dev.ward.domain.buildpolicy.BuildFiles;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the build files of the project root and of the modules the readers report, and never walks
 * the tree: a directory is read only when a build file lists it as a module.
 */
public final class FileSystemBuildFiles implements BuildFiles {
  private static final Logger LOGGER = Logger.getLogger(FileSystemBuildFiles.class.getName());

  private final List<BuildModelReader> readers;

  /** Creates the adapter over the readers of each build tool. */
  public FileSystemBuildFiles(final List<BuildModelReader> readers) {
    this.readers = List.copyOf(readers);
  }

  @Override
  public BuildModel read(final Path root) {
    final Set<String> directories = new LinkedHashSet<>();
    final Deque<String> pending = new ArrayDeque<>(List.of(""));
    while (!pending.isEmpty()) {
      final String directory = pending.poll();
      if (directories.add(directory)) {
        for (final BuildModelReader reader : readers) {
          pending.addAll(reader.modules(root, directory));
        }
      }
    }
    LOGGER.log(Level.FINE, () -> "event=build-files directories=" + directories.size());
    BuildModel model = BuildModel.EMPTY;
    for (final String directory : directories) {
      for (final BuildModelReader reader : readers) {
        model = model.plus(reader.read(root, directory));
      }
    }
    return model;
  }
}
