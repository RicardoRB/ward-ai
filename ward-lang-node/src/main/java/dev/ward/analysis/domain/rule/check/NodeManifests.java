package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The package manifests of a project, by directory, for the dependency checks of dead code. */
final class NodeManifests {

  private static final String MANIFEST = "package.json";
  private static final String NODE_MODULES = "node_modules/";

  private NodeManifests() {}

  /** A parsed {@code package.json}: its path, its directory, and the parsed manifest. */
  record Manifest(String path, String directory, NodeManifest manifest) {}

  /** The parseable manifests of the project, by directory; a file that does not parse is absent. */
  static Map<String, Manifest> byDirectory(final ProjectFiles files) {
    final Map<String, Manifest> byDirectory = new HashMap<>();
    for (final String path : files.paths()) {
      if (isManifest(path)) {
        parsed(files, path).ifPresent(found -> byDirectory.put(found.directory(), found));
      }
    }
    return byDirectory;
  }

  /** The manifest at {@code path}, or empty when its text does not parse. */
  private static Optional<Manifest> parsed(final ProjectFiles files, final String path) {
    return files
        .text(path)
        .flatMap(NodeManifest::parse)
        .map(manifest -> new Manifest(path, directoryOf(path), manifest));
  }

  private static boolean isManifest(final String path) {
    return (MANIFEST.equals(path) || path.endsWith("/" + MANIFEST)) && !path.contains(NODE_MODULES);
  }

  /** The manifest of the nearest directory at or above {@code path}, or empty when none. */
  static Optional<Manifest> nearest(final String path, final Map<String, Manifest> byDirectory) {
    String directory = directoryOf(path);
    while (true) {
      final Manifest found = byDirectory.get(directory);
      if (found != null) {
        return Optional.of(found);
      }
      if (directory.isEmpty()) {
        return Optional.empty();
      }
      directory = directoryOf(directory);
    }
  }

  /** The names that the given sections of a manifest declare. */
  static Set<String> names(final NodeManifest manifest, final List<String> sections) {
    final Set<String> names = new HashSet<>();
    sections.forEach(section -> manifest.entries(section).forEach(entry -> names.add(entry.key())));
    return names;
  }

  /** The string values of the members of a manifest's section, such as scripts. */
  static List<String> texts(final NodeManifest manifest, final String section) {
    final List<String> texts = new ArrayList<>();
    manifest
        .entries(section)
        .forEach(
            entry -> {
              if (entry.value() instanceof Value.Text text) {
                texts.add(text.value());
              }
            });
    return texts;
  }

  static String directoryOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }
}
