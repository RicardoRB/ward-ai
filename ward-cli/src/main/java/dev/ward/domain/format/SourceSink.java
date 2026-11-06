package dev.ward.domain.format;

import java.nio.file.Path;

/** Writes a formatted source file. An adapter does the file system work. */
@FunctionalInterface
public interface SourceSink {

  /**
   * Replaces a file's content in one step, keeping its permissions, so a reader never sees a
   * half-written file.
   *
   * @param file the file
   * @param text the new text, without its byte-order mark
   * @param byteOrderMark whether the file starts with a UTF-8 byte-order mark
   * @throws dev.ward.domain.config.WardException if the file cannot be written
   */
  void persist(Path file, String text, boolean byteOrderMark);
}
