package dev.ward.domain.gate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Outbound port for gates that read the files of the working tree, committed or not. */
public interface WorkingFiles {
  /**
   * Returns the regular files below {@code directory}, relative to {@code root}, as paths relative
   * to that directory with {@code /} separators; empty when the directory does not exist.
   */
  List<String> files(Path root, String directory);

  /** Returns the text of {@code path}, relative to {@code root}, or empty when it is unreadable. */
  Optional<String> read(Path root, String path);
}
