package dev.ward.infrastructure.project;

import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a TypeScript or JavaScript project's manager, workspace and Node version from files. */
final class NodeProjectInspection {
  static final String PACKAGE_JSON = "package.json";
  static final String TSCONFIG = "tsconfig.json";
  private static final String TURBO_JSON = "turbo.json";
  private static final String DEFAULT_NODE_VERSION = "24";
  private static final Pattern LEADING_MAJOR = Pattern.compile("^v?(\\d+)");
  private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
  private static final JsonCodec CODEC = new ForyJsonCodec();

  /** Lock files in precedence order; Bun first because migrated projects keep stale npm locks. */
  private static final List<Map.Entry<String, BuildTool>> LOCK_FILES =
      List.of(
          Map.entry("bun.lock", BuildTool.BUN),
          Map.entry("bun.lockb", BuildTool.BUN),
          Map.entry("pnpm-lock.yaml", BuildTool.PNPM),
          Map.entry("yarn.lock", BuildTool.YARN),
          Map.entry("package-lock.json", BuildTool.NPM));

  /**
   * Framework packages in precedence order: one framework per project, so a site that declares
   * several, such as Astro with Vue islands, is classified by the first one listed here.
   */
  private static final List<Map.Entry<Framework, Set<String>>> FRAMEWORKS =
      List.of(
          Map.entry(Framework.ASTRO, Set.of("astro")),
          Map.entry(Framework.SVELTE, Set.of("svelte", "@sveltejs/kit")),
          Map.entry(Framework.VUE, Set.of("vue", "nuxt")),
          Map.entry(Framework.ANGULAR, Set.of("@angular/core")),
          Map.entry(Framework.NEXT, Set.of("next")),
          Map.entry(Framework.NEST, Set.of("@nestjs/core")),
          Map.entry(Framework.FASTIFY, Set.of("fastify")),
          Map.entry(Framework.EXPRESS, Set.of("express")));

  private static final Map<String, BuildTool> MANAGERS =
      Map.of(
          "npm", BuildTool.NPM,
          "pnpm", BuildTool.PNPM,
          "yarn", BuildTool.YARN,
          "bun", BuildTool.BUN);

  private NodeProjectInspection() {}

  /** Returns the Node project at {@code dir}, which holds {@code tsconfig.json} or package.json. */
  static Project project(final Path dir) {
    final Map<?, ?> manifest = manifest(dir);
    final Language language =
        Files.isRegularFile(dir.resolve(TSCONFIG))
                || NodeWorkspaces.hasTypeScriptMember(dir, manifest)
            ? Language.TYPESCRIPT
            : Language.JAVASCRIPT;
    return new Project(
        dir,
        language,
        manager(dir, manifest),
        framework(manifest),
        false,
        false,
        dir,
        Set.of(language),
        version(dir, manifest),
        taskRunner(dir));
  }

  /** Returns Turborepo when the workspace root holds {@code turbo.json}, else no task runner. */
  private static TaskRunner taskRunner(final Path dir) {
    return Files.isRegularFile(dir.resolve(TURBO_JSON)) ? TaskRunner.TURBO : TaskRunner.NONE;
  }

  /** Reads the framework from declared packages only; file names never decide. */
  private static Framework framework(final Map<?, ?> manifest) {
    final Set<String> declared = new HashSet<>();
    for (final String field : List.of("dependencies", "devDependencies")) {
      if (manifest.get(field) instanceof Map<?, ?> packages) {
        packages.keySet().forEach(name -> declared.add(String.valueOf(name)));
      }
    }
    return FRAMEWORKS.stream()
        .filter(framework -> framework.getValue().stream().anyMatch(declared::contains))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(Framework.NONE);
  }

  private static BuildTool manager(final Path dir, final Map<?, ?> manifest) {
    if (manifest.get("packageManager") instanceof String declared) {
      final int at = declared.indexOf('@');
      final BuildTool tool = MANAGERS.get(at < 0 ? declared : declared.substring(0, at));
      if (tool != null) {
        return tool;
      }
    }
    return LOCK_FILES.stream()
        .filter(lock -> Files.isRegularFile(dir.resolve(lock.getKey())))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(BuildTool.NPM);
  }

  private static String version(final Path dir, final Map<?, ?> manifest) {
    return versionFile(dir, ".nvmrc")
        .or(() -> versionFile(dir, ".node-version"))
        .or(() -> enginesVersion(manifest))
        .orElse(DEFAULT_NODE_VERSION);
  }

  /**
   * Version files hold a version such as {@code v20.11.1}; aliases like {@code lts/*} fall through.
   */
  private static Optional<String> versionFile(final Path dir, final String name) {
    final Path file = dir.resolve(name);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      final Matcher matcher = LEADING_MAJOR.matcher(Files.readString(file).trim());
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  // shortcut: first number in the range, not a semver range parser; upgrade if a real range gives
  // a wrong lower bound.
  private static Optional<String> enginesVersion(final Map<?, ?> manifest) {
    if (manifest.get("engines") instanceof Map<?, ?> engines
        && engines.get("node") instanceof String range) {
      final Matcher matcher = FIRST_NUMBER.matcher(range);
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
    return Optional.empty();
  }

  /** Decodes package.json leniently: a missing, unreadable or malformed file is an empty map. */
  static Map<?, ?> manifest(final Path dir) {
    final Path file = dir.resolve(PACKAGE_JSON);
    if (!Files.isRegularFile(file)) {
      return Map.of();
    }
    try {
      return CODEC.fromJson(Files.readString(file), Object.class) instanceof Map<?, ?> map
          ? map
          : Map.of();
    } catch (IOException | JsonCodecException | IllegalArgumentException e) {
      return Map.of();
    }
  }
}
