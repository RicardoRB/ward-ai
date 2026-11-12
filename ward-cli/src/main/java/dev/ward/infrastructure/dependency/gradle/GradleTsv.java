package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads what the Ward init script writes: one file per project with {@code EDGE}, {@code PROJECT}
 * and {@code UNRESOLVED} lines. Edges whose parent is a project pass through to the module, and the
 * project's own dependencies are the edges whose parent is {@code -}.
 */
final class GradleTsv {
  private static final String PATH_PREFIX = "# path=";
  private static final String SELF = "-";
  private static final String PROJECT = "project:";
  private static final int EDGE_FIELDS = 4;
  private static final int PARENT = 2;
  private static final int CHILD = 3;
  private static final int COORDINATE_PARTS = 3;
  private static final Pattern TAB = Pattern.compile("\t");

  private GradleTsv() {}

  /**
   * Builds the inventory from the files the script wrote.
   *
   * @param files the file texts
   * @param buildFile gives the project-relative build file of a module path
   * @throws DependencyInventoryException on an unresolved dependency or a line it cannot read
   */
  static DependencyInventory read(
      final String project, final List<String> files, final Function<String, String> buildFile) {
    return new DependencyInventory(
        files.stream().map(text -> module(project, text, buildFile)).toList());
  }

  private static ModuleDependencies module(
      final String project, final String text, final Function<String, String> buildFile) {
    final List<String> lines = text.lines().toList();
    if (lines.isEmpty() || !lines.getFirst().startsWith(PATH_PREFIX)) {
      throw unreadable(project, "a result file has no path line");
    }
    final String path = lines.getFirst().substring(PATH_PREFIX.length());
    final String declaringFile = buildFile.apply(path);
    final ModuleBuilder builder = new ModuleBuilder(declaringFile, false);
    lines.subList(1, lines.size()).stream()
        .filter(line -> !line.isBlank())
        .forEach(line -> line(project, path, builder, TAB.split(line, -1), declaringFile));
    return builder.build(path);
  }

  private static void line(
      final String project,
      final String path,
      final ModuleBuilder builder,
      final String[] fields,
      final String declaringFile) {
    switch (fields[0]) {
      case "EDGE" -> edge(project, builder, fields, declaringFile);
      case "PROJECT" -> {
        // a dependency on another project: its packages arrive as edges of its component
      }
      case "UNRESOLVED" ->
          throw new DependencyInventoryException(
              project,
              path.isEmpty() ? "the root project" : "module " + path,
              "the dependency "
                  + field(fields, PARENT)
                  + " could not be resolved: "
                  + field(fields, CHILD),
              "fix the dependency or the repositories, then run again");
      default -> throw unreadable(project, "an unknown result line");
    }
  }

  private static void edge(
      final String project,
      final ModuleBuilder builder,
      final String[] fields,
      final String declaringFile) {
    if (fields.length != EDGE_FIELDS) {
      throw unreadable(project, "an edge line has " + fields.length + " fields");
    }
    final PackageId child = id(project, fields[CHILD]);
    final DependencyScope scope = scope(fields[1]);
    builder.add(child, !child.version().endsWith("-SNAPSHOT")).scope(child, scope);
    if (SELF.equals(fields[PARENT])) {
      builder.direct(child, scope, declaringFile);
    } else if (!fields[PARENT].startsWith(PROJECT)) {
      builder.edge(id(project, fields[PARENT]), child);
    }
  }

  private static PackageId id(final String project, final String coordinates) {
    final String[] parts = coordinates.split(":", COORDINATE_PARTS);
    if (parts.length != COORDINATE_PARTS) {
      throw unreadable(project, "a coordinate is not group:name:version");
    }
    return new PackageId(Ecosystem.MAVEN, parts[0] + ":" + parts[1], parts[2]);
  }

  /** Processors are development, test classpaths test, runtime runtime, compile-only provided. */
  private static DependencyScope scope(final String configuration) {
    final String name = configuration.toLowerCase(java.util.Locale.ROOT);
    if (name.contains("annotationprocessor") || name.startsWith("kapt") || name.startsWith("ksp")) {
      return DependencyScope.DEVELOPMENT;
    }
    if (name.contains("test")) {
      return DependencyScope.TEST;
    }
    return name.endsWith("runtimeclasspath") ? DependencyScope.RUNTIME : DependencyScope.PROVIDED;
  }

  private static String field(final String[] fields, final int index) {
    return index < fields.length ? fields[index] : "";
  }

  private static DependencyInventoryException unreadable(
      final String project, final String reason) {
    return new DependencyInventoryException(
        project, "the Gradle dependency listing", reason, "run the Gradle build to see why");
  }
}
