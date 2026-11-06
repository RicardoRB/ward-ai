package dev.ward.infrastructure.analysis.python;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the {@code .pyi} text of a dotted module name, in the order Python imports it: the project,
 * then the bundled standard-library stubs, then the packages of the project's virtualenv. Nothing
 * runs; a module with no stub is unknown.
 */
public final class PythonStubFiles {

  private static final String BUNDLED = "/dev/ward/analysis/lang/python/stubs/";
  private static final String SUFFIX = ".pyi";
  private static final String INIT = "__init__";
  private static final String SITE_PACKAGES = "site-packages";
  private static final String VIRTUALENV = ".venv";
  private static final String ALTERNATE_VIRTUALENV = "venv";
  private static final String PYTHON_DIRECTORY = "python";
  private static final Pattern SAFE_MODULE =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
  private static final Logger LOGGER = Logger.getLogger(PythonStubFiles.class.getName());

  private final Path projectRoot;

  /** Creates the lookup for the project at {@code projectRoot}. */
  public PythonStubFiles(final Path projectRoot) {
    this.projectRoot = projectRoot;
  }

  /** Returns the stub text of {@code module}, a dotted name, or empty when no stub has it. */
  public Optional<String> text(final String module) {
    if (!SAFE_MODULE.matcher(module).matches()) {
      return Optional.empty();
    }
    final String relative = module.replace('.', '/');
    final Optional<String> project = read(projectRoot, relative);
    if (project.isPresent()) {
      return project;
    }
    final Optional<String> bundled = bundled(relative);
    if (bundled.isPresent()) {
      return bundled;
    }
    for (final Path sitePackages : sitePackages()) {
      final Optional<String> installed = read(sitePackages, relative);
      if (installed.isPresent()) {
        return installed;
      }
    }
    return Optional.empty();
  }

  /** Reads {@code relative}.pyi or {@code relative}/__init__.pyi under {@code root}. */
  private static Optional<String> read(final Path root, final String relative) {
    final Optional<String> module = readFile(root.resolve(relative + SUFFIX));
    return module.isPresent() ? module : readFile(root.resolve(relative).resolve(INIT + SUFFIX));
  }

  private static Optional<String> readFile(final Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException unreadable) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("event=python-stub-unreadable");
      }
      return Optional.empty();
    }
  }

  private static Optional<String> bundled(final String relative) {
    final String resource = BUNDLED + relative + SUFFIX;
    try (InputStream stream = PythonStubFiles.class.getResourceAsStream(resource)) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException unreadable) {
      return Optional.empty();
    }
  }

  /** The site-packages directories of the project's virtualenvs, in a fixed order. */
  private List<Path> sitePackages() {
    final List<Path> found = new ArrayList<>();
    for (final String environment : List.of(VIRTUALENV, ALTERNATE_VIRTUALENV)) {
      final Path lib = projectRoot.resolve(environment).resolve("lib");
      found.add(projectRoot.resolve(environment).resolve("Lib").resolve(SITE_PACKAGES));
      found.addAll(pythonLibraries(lib));
    }
    return found.stream().filter(Files::isDirectory).toList();
  }

  private static List<Path> pythonLibraries(final Path lib) {
    if (!Files.isDirectory(lib)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(lib)) {
      return entries
          .filter(entry -> entry.getFileName().toString().startsWith(PYTHON_DIRECTORY))
          .map(entry -> entry.resolve(SITE_PACKAGES))
          .sorted()
          .toList();
    } catch (IOException unlistable) {
      return List.of();
    }
  }
}
