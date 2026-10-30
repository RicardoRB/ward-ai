package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.TurboWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes a Turborepo workspace's member scripts and the turbo.json tasks its gates need. A script
 * that already exists is kept, and an existing task keeps its settings. Every file that changes is
 * recorded, so a failure restores them all.
 */
final class TurborepoWriter {
  static final String TURBO_JSON = "turbo.json";
  private static final String PACKAGE_JSON = "package.json";
  private static final String SCRIPTS = "scripts";
  private static final String TEST = "test";
  private static final String BUILD = "build";
  private static final String MUTATION = "mutation";

  private TurborepoWriter() {}

  /** One file as it was before the writer changed it. */
  record Written(Path file, String original) {}

  /** The files changed, and the scripts kept because they already differ from the plan. */
  record Result(List<Written> written, List<String> kept) {
    /** Copies the lists. */
    Result {
      written = List.copyOf(written);
      kept = List.copyOf(kept);
    }
  }

  /**
   * Writes {@code workspace}. A failure restores every file already changed, then rethrows.
   *
   * @throws WardException if a file cannot be read or written, or its layout cannot be located
   */
  static Result apply(final TurboWorkspace workspace) {
    final List<Written> written = new ArrayList<>();
    final List<String> kept = new ArrayList<>();
    try {
      for (final TurboWorkspace.Member member : workspace.members()) {
        writeScripts(member, written, kept);
      }
      writeTasks(workspace, written);
      return new Result(written, kept);
    } catch (WardException e) {
      rollback(written);
      throw e;
    }
  }

  /** Restores the files in {@code written}, last changed first. */
  static void rollback(final List<Written> written) {
    for (int i = written.size() - 1; i >= 0; i--) {
      final Written change = written.get(i);
      try {
        Files.writeString(change.file(), change.original());
      } catch (IOException e) {
        throw new WardException("Could not roll back " + change.file() + ": " + e.getMessage(), e);
      }
    }
  }

  private static void writeScripts(
      final TurboWorkspace.Member member, final List<Written> written, final List<String> kept) {
    final Path file = member.directory().resolve(PACKAGE_JSON);
    final String original = read(file);
    String edited = original;
    for (final Map.Entry<String, String> script : member.scripts().entrySet()) {
      final String quoted = PackageJsonEdit.quoted(script.getValue());
      final Optional<String> existing = JsonMembers.stringText(edited, SCRIPTS, script.getKey());
      if (existing.isEmpty()) {
        edited = PackageJsonEdit.withScript(edited, script.getKey(), script.getValue());
      } else if (!existing.get().equals(quoted)) {
        kept.add(member.name() + " keeps its " + script.getKey() + " script");
      }
    }
    if (!edited.equals(original)) {
      replace(file, original, edited, written);
    }
  }

  private static void writeTasks(final TurboWorkspace workspace, final List<Written> written) {
    final Path file = workspace.root().resolve(TURBO_JSON);
    if (!Files.isRegularFile(file)) {
      throw new WardException("Ward cannot write turbo.json: " + file + " does not exist.");
    }
    final String original = read(file);
    final Map<String, String> definitions = new LinkedHashMap<>();
    for (final String task : workspace.tasks()) {
      definitions.put(task, definition(task, original));
    }
    final String edited = TurboJsonEdit.addTasks(original, definitions);
    if (!edited.equals(original)) {
      replace(file, original, edited, written);
    }
  }

  /**
   * Returns the settings of a new turbo task. Tests write coverage, and depend on the packages'
   * builds only when the workspace declares a {@code build} task. Mutation tests write reports.
   */
  private static String definition(final String task, final String turboJson) {
    if (TEST.equals(task)) {
      final boolean build = JsonMembers.names(turboJson, "tasks").contains(BUILD);
      return build
          ? "{\"dependsOn\": [\"^build\"], \"outputs\": [\"coverage/**\"]}"
          : "{\"outputs\": [\"coverage/**\"]}";
    }
    if (MUTATION.equals(task)) {
      return "{\"outputs\": [\"reports/**\"]}";
    }
    return "{}";
  }

  private static void replace(
      final Path file, final String original, final String edited, final List<Written> written) {
    written.add(new Written(file, original));
    try {
      Files.writeString(file, edited);
    } catch (IOException e) {
      throw new WardException("Could not write " + file + ": " + e.getMessage(), e);
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
