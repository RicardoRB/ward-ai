package dev.ward.domain.gate;

import java.util.List;
import java.util.Optional;

/** The files of one commit, read one at a time. Closing it releases what Git holds for it. */
public interface RevisionSnapshot extends AutoCloseable {
  /**
   * Returns the text of {@code path}, relative to the project root, or empty when it does not exist.
   *
   * @throws RevisionReadException when the file exists but Git cannot read it
   */
  Optional<String> read(String path);

  /** Returns the files below {@code directory} as paths relative to it; see {@link RevisionFiles}. */
  List<String> files(String directory);

  @Override
  void close();
}
