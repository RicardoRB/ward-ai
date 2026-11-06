package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfiguration;
import dev.ward.domain.format.FormatConfigurationException;
import dev.ward.domain.format.FormatSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads formatting settings from the {@code .editorconfig} files of a project. This is the only
 * part of the format configuration that touches the file system.
 */
public final class EditorConfigFormatConfiguration implements FormatConfiguration {

  private static final char BYTE_ORDER_MARK = '﻿';

  private final Path projectRoot;

  /**
   * Creates the configuration for one project.
   *
   * @param projectRoot the project root; lookup never goes above it
   * @throws NullPointerException if the root is missing
   */
  public EditorConfigFormatConfiguration(final Path projectRoot) {
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
  }

  @Override
  public FormatSettings resolve(
      final String relativePath,
      final AnalysisLanguage language,
      final List<FormatOption> declared) {
    return EditorConfigSettings.resolve(relativePath, declared, applicableFiles(relativePath));
  }

  /**
   * Returns the {@code .editorconfig} files that apply to a file, farthest first. The walk starts
   * at the file's folder, stops after a file that sets {@code root = true}, and never passes the
   * project root.
   */
  private List<EditorConfigFile> applicableFiles(final String relativePath) {
    final List<EditorConfigFile> closestFirst = new ArrayList<>();
    for (final String directory : directoriesUpward(relativePath)) {
      final Optional<EditorConfigFile> file = read(directory);
      if (file.isPresent()) {
        closestFirst.add(file.get());
        if (file.get().root()) {
          break;
        }
      }
    }
    Collections.reverse(closestFirst);
    return List.copyOf(closestFirst);
  }

  /** Lists the folders from the file's folder up to the project root, which is the empty path. */
  private static List<String> directoriesUpward(final String relativePath) {
    final List<String> directories = new ArrayList<>();
    String directory = parentOf(relativePath);
    directories.add(directory);
    while (!directory.isEmpty()) {
      directory = parentOf(directory);
      directories.add(directory);
    }
    return directories;
  }

  private static String parentOf(final String relativePath) {
    final int slash = relativePath.lastIndexOf('/');
    return slash < 0 ? "" : relativePath.substring(0, slash);
  }

  private Optional<EditorConfigFile> read(final String directory) {
    final Path path = projectRoot.resolve(directory).resolve(EditorConfigFile.FILE_NAME);
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    final String relative =
        directory.isEmpty()
            ? EditorConfigFile.FILE_NAME
            : directory + "/" + EditorConfigFile.FILE_NAME;
    try {
      final String text = Files.readString(path, StandardCharsets.UTF_8);
      final String body =
          !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
      return Optional.of(EditorConfigParser.parse(directory, body));
    } catch (final IOException exception) {
      throw new FormatConfigurationException(
          "Could not read " + relative + " as UTF-8.", exception);
    }
  }
}
