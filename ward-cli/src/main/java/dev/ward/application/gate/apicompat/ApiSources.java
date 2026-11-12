package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.WorkingFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Reads the main source files of a project at the baseline and in the working tree. */
final class ApiSources {
  private static final String POM = "pom.xml";
  private static final List<String> GRADLE_SCRIPTS = List.of("build.gradle.kts", "build.gradle");
  private static final String JAVA = ".java";

  private final ApiCompatPorts ports;
  private final Path root;

  ApiSources(final ApiCompatPorts ports, final Path root) {
    this.ports = ports;
    this.root = root;
  }

  /** Returns the Java files of the main source roots at {@code baseline}. */
  List<SourceUnit> atBaseline(final Baseline baseline) {
    final RevisionFiles git = ports.revisions();
    final String commit = baseline.commit();
    final List<String> roots =
        ports
            .layout()
            .of(git.read(root, commit, POM), gradle(script -> git.read(root, commit, script)));
    return roots.stream()
        .flatMap(
            directory ->
                git.files(root, commit, directory).stream()
                    .filter(file -> file.endsWith(JAVA))
                    .map(file -> directory + "/" + file))
        .sorted()
        .map(
            path ->
                unit(
                    path,
                    git.read(root, commit, path),
                    "Cannot read " + path + " at " + baseline.ref() + "."))
        .toList();
  }

  /** Returns the Java files of the main source roots in the working tree. */
  List<SourceUnit> inWorkingTree() {
    final WorkingFiles working = ports.working();
    final List<String> roots =
        ports.layout().of(working.read(root, POM), gradle(script -> working.read(root, script)));
    return roots.stream()
        .flatMap(
            directory ->
                working.files(root, directory).stream()
                    .filter(file -> file.endsWith(JAVA))
                    .map(file -> directory + "/" + file))
        .sorted()
        .map(
            path ->
                unit(
                    path,
                    working.read(root, path),
                    "Cannot read " + path + " in the working tree."))
        .toList();
  }

  private static Optional<String> gradle(final Function<String, Optional<String>> read) {
    return GRADLE_SCRIPTS.stream().map(read).flatMap(Optional::stream).findFirst();
  }

  private static SourceUnit unit(
      final String path, final Optional<String> text, final String failure) {
    return new SourceUnit(
        path, AnalysisLanguage.JAVA, text.orElseThrow(() -> new ApiCompatException(failure)));
  }
}
