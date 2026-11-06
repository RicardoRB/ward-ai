package dev.ward.domain.format;

import java.nio.file.Path;

/** Reads a source file for formatting. An adapter does the file system work. */
@FunctionalInterface
public interface SourceReader {

  /**
   * Reads a file as UTF-8, without its byte-order mark.
   *
   * @param file the file
   * @return the text and whether the file began with a byte-order mark
   * @throws dev.ward.domain.config.WardException if the file cannot be read as UTF-8
   */
  SourceText read(Path file);
}
