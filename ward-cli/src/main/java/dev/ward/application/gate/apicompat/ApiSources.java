package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.RevisionSnapshot;
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
  private static final String CARGO_SOURCES = "src";

  private final ApiCompatPorts ports;
  private final Path root;

  private final boolean csharp;

  ApiSources(final ApiCompatPorts ports, final Path root, final boolean csharp) {
    this.ports = ports;
    this.root = root;
    this.csharp = csharp;
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
    try (RevisionSnapshot snapshot = git.open(root, commit)) {
      return read(new Tree(snapshot::read, snapshot::files, " at " + baseline.ref()));
    }
  }

  /** Returns the Java and Kotlin files of the main source roots in the working tree. */
  Sources inWorkingTree() {
    final WorkingFiles working = ports.working();
    return read(
        new Tree(
            path -> working.read(root, path),
            directory -> working.files(root, directory),
            " in the working tree"
        )
    );
  }

  private Sources read(final Tree tree) {
    final List<String> modules = new ArrayList<>(modules(tree));
    final List<String> files = new ArrayList<>();
    modules.forEach(module -> files.addAll(sourceFiles(tree, module)));
    if (csharp) {
      final CSharpSources.Found found = CSharpSources.find(tree.files());
      found.modules().stream().filter(module -> !modules.contains(module)).forEach(modules::add);
      files.addAll(found.files());
    }
    final List<SourceUnit> units =
        files.stream()
            .distinct()
            .sorted()
            .map(path -> unit(path, tree.read().apply(path), "Cannot read " + path + tree.where()))
            .map(ports.surfaces()::declarations)
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
    final List<String> listed =
        new ArrayList<>(
            ports.modules().of(tree.read().apply(join(dir, POM)), first(tree, dir, GRADLE_SETTINGS))
        );
    listed.addAll(ApiCargoCrates.members(tree.read(), tree.files(), dir));
    return listed;
  }

  private static boolean hasBuildFile(final Tree tree, final String dir) {
    return tree.read().apply(join(dir, POM)).isPresent()
        || first(tree, dir, GRADLE_SCRIPTS).isPresent()
        || ApiCargoCrates.has(tree.read(), dir);
  }

  private List<String> sourceFiles(final Tree tree, final String module) {
    final List<String> roots =
        ApiCargoCrates.only(tree.read(), module)
            ? List.of(CARGO_SOURCES)
            : ports.layout().of(
                tree.read().apply(join(module, POM)),
                first(tree, module, GRADLE_SCRIPTS)
            );
    return roots.stream()
        .map(directory -> join(module, directory))
        .flatMap(
            directory ->
                tree.files().apply(directory).stream()
                    .filter(ApiSourceLanguages::isSource)
                    .map(file -> directory + "/" + file)
        )
        .toList();
  }

  private static Optional<String> first(
      final Tree tree,
      final String dir,
      final List<String> scripts
  ) {
    return scripts.stream()
        .map(script -> tree.read().apply(join(dir, script)))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static String join(final String dir, final String name) {
    return dir.isEmpty() ? name : dir + "/" + name;
  }

  private static SourceUnit unit(
      final String path,
      final Optional<String> text,
      final String failure
  ) {
    return new SourceUnit(
        path,
        ApiSourceLanguages.of(path),
        text.orElseThrow(() -> new ApiCompatException(failure + "."))
    );
  }

  /** A file tree at one revision: a reader, a lister and how to name the revision. */
  private record Tree(
      Function<String,
      Optional<String>> read,
      Function<String,
      List<String>> files,
      String where
  ) {}
}
