package dev.ward.domain.dependency;

import dev.ward.domain.project.BuildTool;
import java.util.Map;

/** Chooses the {@link DependencySource} of a build tool. */
public final class DependencySources {
  private DependencySources() {}

  /**
   * Returns the source of {@code tool} from {@code readers}, the injected sources by build tool. A
   * build tool with no reader (PDM, Hatch, Go, Cargo, .NET, Tuist and XcodeGen) gets a source that
   * fails naming it, never an empty inventory.
   */
  public static DependencySource forBuildTool(
      final BuildTool tool, final Map<BuildTool, DependencySource> readers) {
    final DependencySource reader = readers.get(tool);
    return switch (tool) {
      case PDM, HATCH, GO, CARGO, DOTNET, TUIST, XCODEGEN -> new UnsupportedDependencySource(tool);
      default -> reader == null ? new UnsupportedDependencySource(tool) : reader;
    };
  }
}
