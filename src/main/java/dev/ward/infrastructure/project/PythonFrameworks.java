package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Framework;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads a Python project's framework from its declared packages, and Django's settings module from
 * {@code manage.py}. A project that declares no framework is {@link Framework#NONE}.
 */
public final class PythonFrameworks {
  private static final Logger LOGGER = Logger.getLogger(PythonFrameworks.class.getName());
  private static final String PYPROJECT = "pyproject.toml";
  private static final String MANAGE = "manage.py";
  private static final String DJANGO = "django";
  private static final String FASTAPI = "fastapi";
  private static final String FLASK = "flask";
  private static final Pattern SETTINGS =
      Pattern.compile("DJANGO_SETTINGS_MODULE[\"'],\\s*[\"']([\\w.]+)");

  private PythonFrameworks() {}

  /**
   * Returns the framework of the project at {@code dir}, first match wins: Django (declared or a
   * manage.py), then FastAPI, then Flask.
   */
  public static Framework of(final Path dir) {
    final Set<String> declared = declared(dir);
    if (declared.contains(DJANGO) || Files.isRegularFile(dir.resolve(MANAGE))) {
      return Framework.DJANGO;
    }
    if (declared.contains(FASTAPI)) {
      return Framework.FASTAPI;
    }
    return declared.contains(FLASK) ? Framework.FLASK : Framework.NONE;
  }

  /**
   * Returns the Django settings module of a project of {@code framework}, or empty for any other
   * framework or a project with no {@code manage.py} that names one.
   */
  public static Optional<String> djangoSettings(final Path dir, final Framework framework) {
    return framework == Framework.DJANGO ? djangoSettings(dir) : Optional.empty();
  }

  /** Returns the Django settings module that {@code manage.py} sets as its default. */
  public static Optional<String> djangoSettings(final Path dir) {
    final Path manage = dir.resolve(MANAGE);
    if (!Files.isRegularFile(manage)) {
      return Optional.empty();
    }
    final Matcher matcher = SETTINGS.matcher(read(manage));
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  /**
   * Returns the normalized names the project declares in {@code pyproject.toml} and its
   * requirements files. A file that does not parse declares nothing, so detection never fails on
   * it.
   */
  private static Set<String> declared(final Path dir) {
    final Set<String> names = new HashSet<>();
    final Path pyproject = dir.resolve(PYPROJECT);
    if (Files.isRegularFile(pyproject)) {
      try {
        names.addAll(PyprojectDependencies.declared(read(pyproject)));
      } catch (WardException invalid) {
        LOGGER.fine(() -> "event=framework-pyproject-invalid declares=none");
      }
    }
    requirementFiles(dir)
        .forEach(
            file ->
                read(file)
                    .lines()
                    .forEach(line -> PyprojectDependencies.nameOf(line).ifPresent(names::add)));
    return names;
  }

  private static java.util.List<Path> requirementFiles(final Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(file -> file.toString().matches(".*requirements[^/]*\\.txt")).toList();
    } catch (IOException e) {
      throw new WardException("Could not list " + dir + ": " + e.getMessage(), e);
    }
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
