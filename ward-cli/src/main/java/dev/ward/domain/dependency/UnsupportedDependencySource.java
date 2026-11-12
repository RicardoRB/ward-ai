package dev.ward.domain.dependency;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;

/** The source of a build tool that has no inventory reader: it fails naming the tool. */
final class UnsupportedDependencySource implements DependencySource {
  private final BuildTool tool;

  UnsupportedDependencySource(final BuildTool tool) {
    this.tool = tool;
  }

  @Override
  public DependencyInventory read(final Project project) {
    throw new DependencyInventoryException(
        project.root().toString(),
        tool.displayName(),
        tool.displayName() + " projects are not supported by the dependency inventory",
        "use a supported build tool");
  }
}
