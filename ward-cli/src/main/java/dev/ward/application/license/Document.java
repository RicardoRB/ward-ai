package dev.ward.application.license;

import java.util.List;

/**
 * A generated document and the problems worth reporting beside it.
 *
 * @param content the document text, ending in a newline
 * @param warnings one line per package whose license is unknown; never fails the command
 */
public record Document(String content, List<String> warnings) {
  /** Copies the warnings. */
  public Document {
    warnings = List.copyOf(warnings);
  }
}
