package dev.ward.infrastructure.buildpolicy.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A POM with the parents that belong to the project: what its properties and managed plugin
 * versions are, and whether it also inherits from a parent outside the project, which Ward does not
 * read.
 */
final class PomChain {
  private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
  private static final int MAX_DEPTH = 16;

  private final PomNode pom;
  private final Map<String, String> properties = new HashMap<>();
  private final Map<String, String> managedPlugins = new HashMap<>();
  private final Set<String> declaredPlugins = new HashSet<>();
  private boolean external;

  private PomChain(final PomNode pom) {
    this.pom = pom;
  }

  /** Loads {@code pom} and its parents inside {@code root}. */
  static PomChain load(final Path root, final Path pom) {
    final List<PomNode> chain = new ArrayList<>();
    final Path base = root.toAbsolutePath().normalize();
    Path current = pom.toAbsolutePath().normalize();
    boolean outside = false;
    for (int depth = 0; depth < MAX_DEPTH; depth++) {
      final PomNode node = PomParser.parse(current, base.relativize(current).toString());
      chain.add(node);
      final Optional<Path> parent = parentPom(base, current, node);
      outside = node.child("parent") != null && parent.isEmpty();
      if (parent.isEmpty()) {
        break;
      }
      current = parent.get();
    }
    final PomChain result = new PomChain(chain.getFirst());
    result.external = outside;
    for (int index = chain.size() - 1; index >= 0; index--) {
      result.absorb(chain.get(index));
    }
    return result;
  }

  /** Returns the parent POM when {@code node} names one that exists inside the project. */
  private static Optional<Path> parentPom(final Path base, final Path current, final PomNode node) {
    final PomNode parent = node.child("parent");
    if (parent == null) {
      return Optional.empty();
    }
    final String path =
        parent.child("relativePath") == null ? "../pom.xml" : parent.text("relativePath");
    final Path next = current.resolveSibling(path).normalize();
    final Path pom = Files.isDirectory(next) ? next.resolve("pom.xml") : next;
    return Files.isRegularFile(pom) && pom.startsWith(base) ? Optional.of(pom) : Optional.empty();
  }

  private void absorb(final PomNode node) {
    for (final PomNode property : node.list("properties")) {
      properties.put(property.name(), property.text().strip());
    }
    for (final PomNode plugin : node.list("build", "pluginManagement", "plugins")) {
      final String version = plugin.text("version");
      if (!version.isEmpty()) {
        managedPlugins.put(plugin.text("artifactId"), resolve(version));
      }
      declaredPlugins.add(plugin.text("artifactId"));
    }
    for (final PomNode plugin : node.list("build", "plugins")) {
      declaredPlugins.add(plugin.text("artifactId"));
    }
  }

  PomNode pom() {
    return pom;
  }

  boolean externalParent() {
    return external;
  }

  /** Returns the managed version of a plugin, or {@code null}. */
  String managed(final String artifact) {
    return managedPlugins.get(artifact);
  }

  boolean declares(final String artifact) {
    return declaredPlugins.contains(artifact);
  }

  /** Replaces the {@code ${property}} references that the project's own POMs define. */
  String resolve(final String text) {
    String result = text.strip();
    for (int pass = 0; pass < MAX_DEPTH && result.contains("${"); pass++) {
      final String next =
          PROPERTY
              .matcher(result)
              .replaceAll(
                  match ->
                      Matcher.quoteReplacement(
                          properties.getOrDefault(match.group(1), match.group())));
      if (next.equals(result)) {
        break;
      }
      result = next;
    }
    return result;
  }

  /** Returns the packaging of the POM, {@code jar} when it has none. */
  String packaging() {
    final String packaging = pom.text("packaging");
    return packaging.isEmpty() ? "jar" : packaging;
  }
}
