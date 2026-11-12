package dev.ward.domain.buildpolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * What the build files of a project declare, in the neutral form the rules read.
 *
 * @param declarations the plugins, dependencies and requirements
 * @param tools the declared build tool versions
 */
public record BuildModel(List<Declaration> declarations, List<ToolVersion> tools) {
  /** An empty model. */
  public static final BuildModel EMPTY = new BuildModel(List.of(), List.of());

  /** Copies the lists. */
  public BuildModel {
    declarations = List.copyOf(declarations);
    tools = List.copyOf(tools);
  }

  /** Returns this model followed by {@code other}. */
  public BuildModel plus(final BuildModel other) {
    final List<Declaration> all = new ArrayList<>(declarations);
    all.addAll(other.declarations);
    final List<ToolVersion> versions = new ArrayList<>(tools);
    versions.addAll(other.tools);
    return new BuildModel(all, versions);
  }
}
