package dev.ward.infrastructure.init.gradle;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A Gradle build script being edited in memory, with the tools each change adds. */
final class GradleBuildFileEdit {
  private final Path buildFile;
  private final String original;
  private final List<String> tools = new ArrayList<>();
  private final List<String> details = new ArrayList<>();
  private String content;

  GradleBuildFileEdit(final Path buildFile, final String original) {
    this.buildFile = buildFile;
    this.original = original;
    this.content = original;
  }

  /** Reads {@code buildFile} into an edit; a read failure names the file. */
  static GradleBuildFileEdit read(final Path buildFile) {
    try {
      return new GradleBuildFileEdit(buildFile, Files.readString(buildFile));
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Gradle tools in " + buildFile + ": " + e.getMessage(), e);
    }
  }

  Path buildFile() {
    return buildFile;
  }

  String original() {
    return original;
  }

  String content() {
    return content;
  }

  List<String> tools() {
    return List.copyOf(tools);
  }

  List<String> details() {
    return List.copyOf(details);
  }

  boolean contains(final String text) {
    return content.contains(text);
  }

  void addPlugins(final List<String> declarations) {
    content = GradlePluginsBlock.add(content, declarations);
  }

  void append(final String block, final String tool, final String version) {
    content = content + block + "\n";
    record(tool, version);
  }

  void record(final String tool, final String version) {
    tools.add(tool);
    details.add("+ " + tool + (version.isBlank() ? "" : " " + version));
  }
}
