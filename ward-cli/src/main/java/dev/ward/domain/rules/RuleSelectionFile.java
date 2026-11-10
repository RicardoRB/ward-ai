package dev.ward.domain.rules;

import java.nio.file.Path;
import java.util.Optional;

/** Port that reads and writes {@code ward-rules.toml} at the root of a project. */
public interface RuleSelectionFile {

  /** Returns the file text, or empty when the file does not exist. */
  Optional<String> read(Path root);

  /**
   * Replaces the file with {@code text}. The text must be valid TOML; an adapter refuses any other
   * text and leaves the file as it was.
   *
   * @throws dev.ward.domain.config.WardException if the text is not valid TOML or the file cannot
   *     be written
   */
  void save(Path root, String text);
}
