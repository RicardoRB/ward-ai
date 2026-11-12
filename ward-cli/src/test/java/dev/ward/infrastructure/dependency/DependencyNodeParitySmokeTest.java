package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.node.NodeDependencySource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compares the Node inventory with npm and pnpm. */
class DependencyNodeParitySmokeTest {
  private static final String LOCAL = "local:";

  @Test
  void npmMatchesNpmLsAll(@TempDir final Path work) throws IOException {
    ParitySupport.enabled("npm");
    final Path dir = ParitySupport.copy("npm-workspace", work);
    ParitySupport.run(dir, "npm", "install", "--ignore-scripts", "--no-audit", "--no-fund");
    final Set<String> listed = new TreeSet<>();
    collectNpm(
        ParitySupport.CODEC.fromJson(
            ParitySupport.run(dir, "npm", "ls", "--all", "--json"), Object.class),
        listed);

    final DependencyInventory inventory =
        new NodeDependencySource(ParitySupport.CODEC)
            .read(new Project(dir, Language.JAVASCRIPT, BuildTool.NPM, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.NPM))
        .isEqualTo(withoutMembers(listed));
  }

  @Test
  void pnpmMatchesPnpmLsRecursive(@TempDir final Path work) throws IOException {
    ParitySupport.enabled("pnpm");
    final Path dir = ParitySupport.copy("pnpm-workspace", work);
    ParitySupport.run(dir, "pnpm", "install", "--ignore-scripts");
    final Set<String> listed = new TreeSet<>();
    final Object projects =
        ParitySupport.CODEC.fromJson(
            ParitySupport.run(dir, "pnpm", "ls", "--recursive", "--depth", "Infinity", "--json"),
            Object.class);
    if (projects instanceof List<?> list) {
      list.forEach(project -> collectNpm(project, listed));
    }

    final DependencyInventory inventory =
        new NodeDependencySource(ParitySupport.CODEC)
            .read(new Project(dir, Language.JAVASCRIPT, BuildTool.PNPM, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.NPM))
        .isEqualTo(withoutMembers(listed));
  }

  /** Drops the workspace members that a listing marks with {@code local:<name>}. */
  private static Set<String> withoutMembers(final Set<String> listed) {
    final Set<String> members =
        listed.stream()
            .filter(entry -> entry.startsWith(LOCAL))
            .map(entry -> entry.substring(LOCAL.length()) + "@")
            .collect(Collectors.toSet());
    return listed.stream()
        .filter(entry -> !entry.startsWith(LOCAL))
        .filter(entry -> members.stream().noneMatch(entry::startsWith))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static void collectNpm(final Object node, final Set<String> listed) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    for (final String section :
        List.of("dependencies", "devDependencies", "optionalDependencies")) {
      if (map.get(section) instanceof Map<?, ?> dependencies) {
        dependencies.forEach((name, child) -> collectNpmEntry(name, child, listed));
      }
    }
  }

  private static void collectNpmEntry(
      final Object name, final Object child, final Set<String> listed) {
    if (child instanceof Map<?, ?> info) {
      final String version = info.get("version") instanceof String text ? text : "";
      if (String.valueOf(info.get("resolved")).startsWith("file:") || version.startsWith("link:")) {
        listed.add(LOCAL + name);
      } else if (!version.isEmpty()) {
        listed.add(name + "@" + version);
      }
      collectNpm(child, listed);
    }
  }
}
