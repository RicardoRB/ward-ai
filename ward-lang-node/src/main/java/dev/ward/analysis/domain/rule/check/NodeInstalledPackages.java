package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The installed packages a project carries under {@code node_modules}, for the binary check. */
final class NodeInstalledPackages {

  private static final String MANIFEST = "package.json";
  private static final Pattern INSTALLED =
      Pattern.compile("node_modules/((?:@[^/]+/)?[^/]+)/package\\.json");

  private NodeInstalledPackages() {}

  /**
   * The installed packages whose manifests the project carries, by package name, read from {@code
   * node_modules/<name>/package.json} at the root only.
   */
  static Map<String, NodeManifests.Manifest> installed(final ProjectFiles files) {
    final Map<String, NodeManifests.Manifest> installed = new HashMap<>();
    for (final String path : files.paths()) {
      final Matcher matcher = INSTALLED.matcher(path);
      if (matcher.matches()) {
        installedAt(files, path, matcher.group(1))
            .ifPresent(found -> installed.put(found.getKey(), found.getValue()));
      }
    }
    return installed;
  }

  private static Optional<Map.Entry<String, NodeManifests.Manifest>> installedAt(
      final ProjectFiles files, final String path, final String name) {
    final String directory = path.substring(0, path.length() - MANIFEST.length() - 1);
    return files
        .text(path)
        .flatMap(NodeManifest::parse)
        .map(manifest -> Map.entry(name, new NodeManifests.Manifest(path, directory, manifest)));
  }

  /**
   * The command names a package's {@code bin} field installs: its name, or the keys of an object.
   */
  static List<String> binaryNames(final String packageName, final NodeManifest manifest) {
    final Optional<Member> bin = manifest.member("bin");
    if (bin.isEmpty()) {
      return List.of();
    }
    if (bin.get().value() instanceof Value.Text) {
      return List.of(packageName.substring(packageName.lastIndexOf('/') + 1));
    }
    return bin.get().value() instanceof Value.Obj object
        ? object.members().stream().map(Member::key).toList()
        : List.of();
  }
}
