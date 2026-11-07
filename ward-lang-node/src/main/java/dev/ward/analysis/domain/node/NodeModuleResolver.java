package dev.ward.analysis.domain.node;

import java.util.Optional;

/**
 * Resolves a module specifier written in a project file to another project file: relative paths,
 * the {@code paths} and {@code baseUrl} of the nearest TypeScript config, and workspace packages.
 */
public final class NodeModuleResolver {
  private final NodeSpecifiers specifiers;

  /** Creates the resolver over the project described by {@code layout}. */
  public NodeModuleResolver(final NodeProjectLayout layout) {
    this.specifiers = new NodeSpecifiers(layout);
  }

  /** Returns the project file that {@code specifier}, written in {@code from}, names, or empty. */
  public Optional<String> resolve(final String from, final String specifier) {
    return specifiers.resolve(from, new NodeReference("static", specifier));
  }
}
