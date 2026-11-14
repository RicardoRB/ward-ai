package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.WorkingFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads the main source files of every module of a project at the baseline and in the working tree.
 */
final class ApiSources {
  private static final String POM = "pom.xml";
  private static final List<String> GRADLE_SCRIPTS = List.of("build.gradle.kts", "build.gradle");
  private static final List<String> GRADLE_SETTINGS =
      List.of("settings.gradle.kts", "settings.gradle");
  private static final String JAVA = ".java";
  private static final String KOTLIN = ".kt";

  private final ApiCompatPorts ports;
  private final Path root;

  ApiSources(final ApiCompatPorts ports, final Path root) {
    this.ports = ports;
    this.root = root;
  }

  /** The source files of a revision and the module directories they belong to. */
  record Sources(List<SourceUnit> units, List<String> modules) {
    Sources {
      units = List.copyOf(units);
      modules = List.copyOf(modules);
    }
  }

  /** Returns the Java and Kotlin files of the main source roots at {@code baseline}. */
  Sources atBaseline(final Baseline baseline) {
    final RevisionFiles git = ports.revisions();
    final String commit = baseline.commit();
    return read(
        new Tree(
            path -> git.read(root, commit, path),
            directory -> git.files(root, commit, directory),
            " at " + baseline.ref()));
  }

  /** Returns the Java and Kotlin files of the main source roots in the working tree. */
  Sources inWorkingTree() {
    final WorkingFiles working = ports.working();
    return read(
        new Tree(
            path -> working.read(root, path),
            directory -> working.files(root, directory),
            " in the working tree"));
  }

  private Sources read(final Tree tree) {
    final List<String> modules = modules(tree);
    final List<SourceUnit> units =
        modules.stream()
            .flatMap(module -> sourceFiles(tree, module).stream())
            .distinct()
            .sorted()
            .map(path -> unit(path, tree.read().apply(path), "Cannot read " + path + tree.where()))
            .toList();
    return new Sources(units, modules);
  }

  /** Returns the root module "" and, below it, every module that has a build file. */
  private List<String> modules(final Tree tree) {
    final List<String> found = new ArrayList<>(List.of(""));
    final List<String> pending = new ArrayList<>(List.of(""));
    while (!pending.isEmpty()) {
      final String dir = pending.remove(0);
      for (final String child : listed(tree, dir)) {
        final String module = join(dir, child);
        if (!found.contains(module) && hasBuildFile(tree, module)) {
          found.add(module);
          pending.add(module);
        }
      }
    }
    return found;
  }

  private List<String> listed(final Tree tree, final String dir) {
    return ports.modules().of(tree.read().apply(join(dir, POM)), first(tree, dir, GRADLE_SETTINGS));
  }

  private static boolean hasBuildFile(final Tree tree, final String dir) {
    return tree.read().apply(join(dir, POM)).isPresent()
        || first(tree, dir, GRADLE_SCRIPTS).isPresent();
  }

  private List<String> sourceFiles(final Tree tree, final String module) {
    return ports
        .layout()
        .of(tree.read().apply(join(module, POM)), first(tree, module, GRADLE_SCRIPTS))
        .stream()
        .map(directory -> join(module, directory))
        .flatMap(
            directory ->
                tree.files().apply(directory).stream()
                    .filter(ApiSources::isSource)
                    .map(file -> directory + "/" + file))
        .toList();
  }

  private static Optional<String> first(
      final Tree tree, final String dir, final List<String> scripts) {
    return scripts.stream()
        .map(script -> tree.read().apply(join(dir, script)))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static String join(final String dir, final String name) {
    return dir.isEmpty() ? name : dir + "/" + name;
  }

  private static boolean isSource(final String file) {
    return file.endsWith(JAVA) || file.endsWith(KOTLIN);
  }

  private static SourceUnit unit(
      final String path, final Optional<String> text, final String failure) {
    return new SourceUnit(
        path,
        path.endsWith(KOTLIN) ? AnalysisLanguage.KOTLIN : AnalysisLanguage.JAVA,
        text.orElseThrow(() -> new ApiCompatException(failure + ".")));
  }

  /** A file tree at one revision: a reader, a lister and how to name the revision. */
  private record Tree(
      Function<String, Optional<String>> read,
      Function<String, List<String>> files,
      String where) {}
}
