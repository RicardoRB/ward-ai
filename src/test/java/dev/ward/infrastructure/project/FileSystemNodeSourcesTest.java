package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which Node packages hold JavaScript sources of their own. */
class FileSystemNodeSourcesTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String MANIFEST = "{\"name\": \"web\"}";

  @TempDir Path dir;

  private final FileSystemNodeSources sources = new FileSystemNodeSources();
  private ProjectTree tree;

  @BeforeEach
  void createTree() throws IOException {
    tree = new ProjectTree(dir);
    tree.write(PACKAGE_JSON, MANIFEST);
  }

  @Test
  void sourceFileCounts() throws IOException {
    tree.write("src/index.js", "module.exports = 1;\n");

    assertThat(sources.hasSources(dir)).isTrue();
  }

  @Test
  void bareRootHasNoSources() throws IOException {
    tree.write("pnpm-lock.yaml", "");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void staticAssetsOfAMavenProjectDoNotCount() throws IOException {
    tree.write("apps/api/pom.xml", "<project/>");
    tree.write("apps/api/src/main/resources/static/app.js", "console.log(1);\n");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void rootToolConfigIsNotASource() throws IOException {
    tree.write("commitlint.config.js", "module.exports = {};\n");
    tree.write("vite.config.mjs", "export default {};\n");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void dependenciesAndBuildOutputDoNotCount() throws IOException {
    tree.write("node_modules/lib/index.js", "");
    tree.write("dist/index.js", "");
    tree.write("coverage/lcov-report/prettify.js", "");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void nestedPackageThatIsNotAMemberDoesNotCount() throws IOException {
    tree.write("tools/release/package.json", MANIFEST);
    tree.write("tools/release/index.js", "");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void pythonPackageBelowNodeRootDoesNotCount() throws IOException {
    tree.write("python/setup.py", "from setuptools import setup\n");
    tree.write("python/static/app.js", "console.log(1);\n");
    tree.write("tools/legacy/setup.cfg", "[metadata]\n");
    tree.write("tools/legacy/build.js", "");

    assertThat(sources.hasSources(dir)).isFalse();
  }

  @Test
  void workspaceMemberSourcesCount() throws IOException {
    tree.write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    tree.write("packages/ui/package.json", MANIFEST);
    tree.write("packages/ui/src/button.jsx", "");

    assertThat(sources.hasSources(dir)).isTrue();
  }
}
