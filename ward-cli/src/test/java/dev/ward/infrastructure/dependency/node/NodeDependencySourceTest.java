package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NodeDependencySourceTest {
  static DependencyInventory read(final Path dir, final BuildTool tool) {
    return new NodeDependencySource(new ForyJsonCodec())
        .read(new Project(dir.toAbsolutePath(), Language.JAVASCRIPT, tool, Framework.NONE));
  }

  @ParameterizedTest
  @CsvSource({
    "npm-v1,NPM",
    "npm-v2,NPM",
    "npm-v3,NPM",
    "npm-workspace,NPM",
    "pnpm-v6,PNPM",
    "pnpm-v9,PNPM",
    "pnpm-workspace,PNPM",
    "yarn-classic,YARN",
    "yarn-classic-workspace,YARN",
    "yarn-berry,YARN",
    "yarn-berry-workspace,YARN",
    "bun,BUN",
    "bun-workspace,BUN"
  })
  void everyLockfileFormatMatchesItsGoldenInventory(final String fixture, final BuildTool tool) {
    final Path dir = InventoryFixtures.fixture(fixture);

    InventoryFixtures.assertMatches(read(dir, tool), dir, "expected.txt");
  }
}
