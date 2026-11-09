package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeModuleScan;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeReference;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a bare import of a package that the nearest {@code package.json} of the importing file
 * does not list in any dependency section, as knip reports an unlisted dependency. A file with no
 * manifest above it, a Node built-in and a {@code #} subpath import are never findings.
 *
 * <p>shortcut: checks the nearest manifest only, so a dependency that only the root manifest lists
 * is reported in a workspace package; a workspace-aware check would read the root manifest too.
 */
public final class NodeUnlistedImportCheck implements RuleCheck, ProjectCheck {

  private static final String MANIFEST = "package.json";
  private static final String RELATIVE = ".";
  private static final String ABSOLUTE = "/";
  private static final String SUBPATH = "#";
  private static final String NODE_PREFIX = "node:";
  private static final String SCOPE = "@";
  private static final String SEPARATOR = "/";
  private static final String VALUE = "{value}";
  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
  private static final Set<String> BUILTINS =
      Set.of(
          "assert",
          "async_hooks",
          "buffer",
          "child_process",
          "cluster",
          "console",
          "constants",
          "crypto",
          "dgram",
          "diagnostics_channel",
          "dns",
          "domain",
          "events",
          "fs",
          "http",
          "http2",
          "https",
          "inspector",
          "module",
          "net",
          "os",
          "path",
          "perf_hooks",
          "process",
          "punycode",
          "querystring",
          "readline",
          "repl",
          "stream",
          "string_decoder",
          "sys",
          "timers",
          "tls",
          "trace_events",
          "tty",
          "url",
          "util",
          "v8",
          "vm",
          "wasi",
          "worker_threads",
          "zlib",
          "test");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the package name
   */
  public NodeUnlistedImportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    final Map<String, Set<String>> listed = listedByDirectory(files);
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          final Optional<Set<String>> declared = nearest(path, listed);
          if (declared.isEmpty()) {
            return;
          }
          for (final Reference reference : references(tree)) {
            final Optional<String> specifier = specifier(reference);
            specifier
                .map(NodeUnlistedImportCheck::packageName)
                .filter(name -> !name.isEmpty() && !declared.get().contains(name))
                .ifPresent(name -> found.add(new ProjectMatch(path, match(reference, name))));
          }
        });
    return List.copyOf(found);
  }

  /** Each manifest's directory, with the names it lists in any dependency section. */
  private static Map<String, Set<String>> listedByDirectory(final ProjectFiles files) {
    final Map<String, Set<String>> listed = new HashMap<>();
    for (final String path : files.paths()) {
      if (path.equals(MANIFEST) || path.endsWith(SEPARATOR + MANIFEST)) {
        files
            .text(path)
            .flatMap(NodeManifest::parse)
            .ifPresent(manifest -> listed.put(directoryOf(path), listedNames(manifest)));
      }
    }
    return listed;
  }

  private static Set<String> listedNames(final NodeManifest manifest) {
    final Set<String> names = new HashSet<>();
    SECTIONS.forEach(section -> manifest.entries(section).forEach(entry -> names.add(entry.key())));
    return names;
  }

  /** The listed names of the nearest manifest at or above {@code path}, or empty when none. */
  private static Optional<Set<String>> nearest(
      final String path, final Map<String, Set<String>> listed) {
    String directory = directoryOf(path);
    while (true) {
      final Set<String> names = listed.get(directory);
      if (names != null) {
        return Optional.of(names);
      }
      if (directory.isEmpty()) {
        return Optional.empty();
      }
      directory = directoryOf(directory);
    }
  }

  private static String directoryOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  private static List<Reference> references(final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return scan.references();
  }

  private static Optional<String> specifier(final Reference reference) {
    return NodeReference.parse(reference.specifier()).map(NodeReference::specifier);
  }

  /** The package an import names, or empty for a relative, absolute, subpath or built-in import. */
  static String packageName(final String specifier) {
    if (specifier.startsWith(RELATIVE)
        || specifier.startsWith(ABSOLUTE)
        || specifier.startsWith(SUBPATH)
        || specifier.startsWith(NODE_PREFIX)
        || BUILTINS.contains(specifier.split(SEPARATOR, 2)[0])) {
      return "";
    }
    final String[] parts = specifier.split(SEPARATOR);
    if (specifier.startsWith(SCOPE)) {
      return parts.length >= 2 ? parts[0] + SEPARATOR + parts[1] : "";
    }
    return parts[0];
  }

  private RuleMatch match(final Reference reference, final String name) {
    return new RuleMatch(reference.span(), message.replace(VALUE, name));
  }
}
