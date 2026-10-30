package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes a Node project's files for the build-tool configurator. */
final class NodeManifestFiles {
  private NodeManifestFiles() {}

  /** Bun reads its coverage threshold only from bunfig.toml, which init never edits. */
  static List<String> bunfigWarning(final Project project, final List<InitGateOption> gates) {
    final Path bunfig = project.root().resolve(NodeConfigFiles.BUNFIG);
    if (gates.stream().noneMatch(gate -> "bun-test".equals(gate.id()))
        || !Files.isRegularFile(bunfig)
        || NodeConfigFiles.owned(read(bunfig))) {
      return List.of();
    }
    return List.of(
        "bunfig.toml is kept as you wrote it; bun-test enforces a coverage threshold only if it"
            + " sets [test] coverageThreshold.");
  }

  /** Returns the packages {@code project} needs for {@code gates}; a Turborepo root adds turbo. */
  static List<NodePackageCatalog.Package> missing(
      final Project project, final List<InitGateOption> gates, final PackageManifest manifest) {
    return NodePackageCatalog.missing(
        project.language(), gates, manifest, project.taskRunner().packageName());
  }

  static Map<String, String> versions(final List<NodePackageCatalog.Package> packages) {
    final Map<String, String> versions = new LinkedHashMap<>();
    packages.forEach(tool -> versions.put(tool.name(), tool.version()));
    return versions;
  }

  static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }

  static void writeText(final Path file, final String content) {
    try {
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new WardException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }
}
