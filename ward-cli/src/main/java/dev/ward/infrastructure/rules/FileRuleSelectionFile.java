package dev.ward.infrastructure.rules;

import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.config.WardException;
import dev.ward.domain.rules.RuleSelectionFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

/**
 * Reads and writes {@code ward-rules.toml} in the project root. A write goes through a temporary
 * file that replaces the original in one move, and text that is not valid TOML is refused.
 */
public final class FileRuleSelectionFile implements RuleSelectionFile {
  private static final Logger LOGGER = Logger.getLogger(FileRuleSelectionFile.class.getName());
  private static final String TEMP_PREFIX = ".ward-rules-";
  private static final String TEMP_SUFFIX = ".tmp";

  /** Creates the adapter; it holds no state. */
  @Inject
  public FileRuleSelectionFile() {
    // Stateless adapter; Dagger constructs it through this constructor.
  }

  @Override
  public Optional<String> read(final Path root) {
    final Path file = root.resolve(RuleConfigFile.DEFAULT_NAME);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    } catch (final IOException exception) {
      throw new WardException(RuleConfigFile.DEFAULT_NAME + " could not be read", exception);
    }
  }

  @Override
  public void save(final Path root, final String text) {
    refuseInvalid(text);
    final Path file = root.resolve(RuleConfigFile.DEFAULT_NAME);
    Path temp = null;
    try {
      temp = Files.createTempFile(root, TEMP_PREFIX, TEMP_SUFFIX);
      Files.writeString(temp, text, StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      LOGGER.log(Level.FINE, "event=rules-file-written file={0}", RuleConfigFile.DEFAULT_NAME);
    } catch (final IOException exception) {
      deleteQuietly(temp);
      throw new WardException(RuleConfigFile.DEFAULT_NAME + " could not be written", exception);
    }
  }

  private static void refuseInvalid(final String text) {
    final TomlParseResult toml = Toml.parse(text);
    if (!toml.hasErrors()) {
      return;
    }
    throw new WardException(
        toml.errors().stream()
            .map(FileRuleSelectionFile::describe)
            .collect(Collectors.joining("\n")));
  }

  private static String describe(final TomlParseError error) {
    return RuleConfigFile.DEFAULT_NAME
        + ": not valid TOML at "
        + error.position()
        + ": "
        + error.getMessage();
  }

  private static void deleteQuietly(final Path temp) {
    if (temp == null) {
      return;
    }
    try {
      Files.deleteIfExists(temp);
    } catch (final IOException exception) {
      LOGGER.log(Level.FINE, "event=rules-temp-delete-failed", exception);
    }
  }
}
