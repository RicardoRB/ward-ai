package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.types.ClassFileException;
import dev.ward.analysis.domain.jvm.types.ClassFileReader;
import dev.ward.analysis.domain.jvm.types.JvmClass;
import dev.ward.analysis.domain.jvm.types.JvmLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** The classes in a directory tree of {@code .class} files, such as {@code target/classes}. */
public final class DirectoryLibrary implements JvmLibrary {
  private static final Logger LOGGER = Logger.getLogger(DirectoryLibrary.class.getName());
  private static final Pattern BINARY_NAME = Pattern.compile("[\\p{L}\\p{N}_$]+(\\.[\\p{L}\\p{N}_$]+)*");

  private final Path root;

  /** Creates the library over the class files below {@code root}. */
  public DirectoryLibrary(final Path root) {
    this.root = root;
  }

  private Optional<Path> file(final String name) {
    if (!BINARY_NAME.matcher(name).matches()) {
      return Optional.empty();
    }
    final Path file = root.resolve(name.replace('.', '/') + ".class");
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  @Override
  public boolean exists(final String name) {
    return file(name).isPresent();
  }

  @Override
  public Optional<JvmClass> find(final String name) {
    final Optional<Path> file = file(name);
    if (file.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(ClassFileReader.read(Files.readAllBytes(file.get())));
    } catch (IOException | ClassFileException unreadable) {
      LOGGER.log(Level.FINE, "event=directory-class-unreadable");
      return Optional.empty();
    }
  }
}
