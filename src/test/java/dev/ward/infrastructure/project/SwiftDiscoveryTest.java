package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Discovery skips dependency checkouts and the inside of Xcode bundles. */
class SwiftDiscoveryTest {
  private static final String PBXPROJ = "// !$*UTF8*$!\n";

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void cocoaPodsAppIsOneXcodeProjectAtTheRoot() throws IOException {
    tree.write("App.xcworkspace/contents.xcworkspacedata", "<Workspace/>");
    tree.write("App.xcodeproj/project.pbxproj", PBXPROJ);
    tree.write("App.xcodeproj/project.xcworkspace/contents.xcworkspacedata", "<Workspace/>");
    tree.write("App/Assets.xcassets/Contents.json", "{}");
    tree.write("Pods/Pods.xcodeproj/project.pbxproj", PBXPROJ);
    tree.write("Carthage/Checkouts/Lib/Package.swift", "// swift-tools-version: 6.0\n");
    tree.write("DerivedData/Build/Package.swift", "// swift-tools-version: 6.0\n");

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
    assertThat(inspection.discover(root))
        .extracting(discovered -> discovered.project().buildTool())
        .containsExactly(BuildTool.XCODE);
  }
}
