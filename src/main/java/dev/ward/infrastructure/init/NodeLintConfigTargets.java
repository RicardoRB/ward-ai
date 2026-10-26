package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.NodeConfigTarget.Disposition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ESLint and Biome configs of a Node project. A project's own config is kept, or under strict
 * it is layered: moved to a base file, with Ward's root config in its place.
 */
final class NodeLintConfigTargets {
  private static final String ESLINT_ROOT = "eslint.config.";
  private static final String ESLINT_BASE = "eslint.base.";
  private static final String BIOME_ROOT = "biome.jsonc";
  private static final String BIOME_BASE = "biome.base.";

  private NodeLintConfigTargets() {}

  /** Returns the ESLint files for {@code project}, with {@code jest} adding Jest's globals. */
  static List<NodeConfigTarget> eslint(
      final Project project,
      final PackageManifest manifest,
      final NodeToolConfigs configs,
      final QualityPreset preset,
      final boolean jest)
      throws IOException {
    final List<NodeConfigTarget> targets = new ArrayList<>();
    final Path root = project.root();
    final boolean typescript = project.language() == Language.TYPESCRIPT;
    final Optional<Path> base = configs.baseConfig(root, NodeConfigFiles.ESLINT);
    final Optional<Path> ward = configs.wardConfig(root, NodeConfigFiles.ESLINT);
    if (typescript && ward.isPresent() && base.isPresent()) {
      // Ward's layer: the root is regenerated, and the base file stays as the user wrote it.
      targets.add(
          NodeConfigTarget.write(
              ward.get(),
              NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
                  + NodeLayerTemplates.eslint(preset, nameOf(root, base.get()))));
      return targets;
    }
    final Optional<Path> user = configs.userConfig(root, NodeConfigFiles.ESLINT);
    if (user.isEmpty()) {
      final String file = manifest.module() ? "eslint.config.js" : "eslint.config.mjs";
      targets.add(
          NodeConfigTarget.write(
              root.resolve(file),
              NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
                  + NodeConfigFiles.eslintConfig(
                      project.language(), manifest, preset, jest, project.framework())));
      return targets;
    }
    if (typescript && preset == QualityPreset.STRICT && isFlat(root, user.get())) {
      targets.addAll(layerEslint(root, user.get(), base, manifest, preset));
      return targets;
    }
    targets.add(NodeConfigTarget.keep(user.get(), notApplied(preset, "ESLint", root, user.get())));
    return targets;
  }

  /** Returns the Biome files for {@code project}: a generated, a kept or a layered config. */
  static List<NodeConfigTarget> biome(
      final Project project, final NodeToolConfigs configs, final QualityPreset preset)
      throws IOException {
    final List<NodeConfigTarget> targets = new ArrayList<>();
    final Path root = project.root();
    final Optional<Path> base = configs.baseConfig(root, NodeConfigFiles.BIOME);
    final Optional<Path> ward = configs.wardConfig(root, NodeConfigFiles.BIOME);
    if (ward.isPresent() && base.isPresent()) {
      targets.add(
          NodeConfigTarget.write(
              ward.get(),
              NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
                  + NodeLayerTemplates.biome(preset, nameOf(root, base.get()))));
      return targets;
    }
    final Optional<Path> user = configs.userConfig(root, NodeConfigFiles.BIOME);
    if (user.isEmpty()) {
      targets.add(
          NodeConfigTarget.write(
              root.resolve(BIOME_ROOT),
              NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
                  + NodeConfigTemplates.biome(preset)));
      return targets;
    }
    // A dot-file config is never moved: Ward keeps it in place under every preset.
    if (preset == QualityPreset.STRICT && !nameOf(root, user.get()).startsWith(".")) {
      targets.addAll(layerBiome(root, user.get(), base, preset));
      return targets;
    }
    targets.add(NodeConfigTarget.keep(user.get(), notApplied(preset, "Biome", root, user.get())));
    return targets;
  }

  /**
   * Moves a flat config to its base file, and writes Ward's root config in its place. The root
   * takes the user's name, unless that name would make it CommonJS; then the root is {@code .mjs}.
   */
  private static List<NodeConfigTarget> layerEslint(
      final Path root,
      final Path user,
      final Optional<Path> base,
      final PackageManifest manifest,
      final QualityPreset preset)
      throws IOException {
    if (base.isPresent()) {
      return List.of(NodeConfigTarget.conflict(base.get()));
    }
    final String name = nameOf(root, user);
    final String ext = extension(name);
    final Path baseFile = root.resolve(ESLINT_BASE + ext);
    final Path rootFile = root.resolve(ESLINT_ROOT + rootExtension(ext, manifest.module()));
    return layer(
        user,
        baseFile,
        rootFile,
        NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
            + NodeLayerTemplates.eslint(preset, nameOf(root, baseFile)));
  }

  /** Moves a Biome config to its base file, and writes Ward's {@code biome.jsonc} in its place. */
  private static List<NodeConfigTarget> layerBiome(
      final Path root, final Path user, final Optional<Path> base, final QualityPreset preset)
      throws IOException {
    if (base.isPresent()) {
      return List.of(NodeConfigTarget.conflict(base.get()));
    }
    final Path baseFile = root.resolve(BIOME_BASE + extension(nameOf(root, user)));
    return layer(
        user,
        baseFile,
        root.resolve(BIOME_ROOT),
        NodeConfigFiles.marker(NodeConfigFiles.JS_MARKER, preset)
            + NodeLayerTemplates.biome(preset, nameOf(root, baseFile)));
  }

  /**
   * Returns the moves of one layer: the user's content to {@code baseFile}, Ward's root, and the
   * user's file's removal.
   */
  private static List<NodeConfigTarget> layer(
      final Path user, final Path baseFile, final Path rootFile, final String rootContent)
      throws IOException {
    final List<NodeConfigTarget> targets = new ArrayList<>();
    targets.add(
        new NodeConfigTarget(
            baseFile,
            Disposition.MOVE,
            Files.readString(user),
            "moved from " + user.getFileName()));
    targets.add(new NodeConfigTarget(rootFile, Disposition.REPLACE, rootContent, null));
    if (!rootFile.equals(user)) {
      targets.add(
          new NodeConfigTarget(
              user, Disposition.REMOVE, null, "moved to " + baseFile.getFileName()));
    }
    return targets;
  }

  /** Returns the root extension for a layer over a {@code ext} config. */
  private static String rootExtension(final String ext, final boolean module) {
    return switch (ext) {
      case "js" -> module ? "js" : "mjs";
      case "cjs" -> "mjs";
      default -> ext;
    };
  }

  private static boolean isFlat(final Path root, final Path file) {
    return nameOf(root, file).startsWith(ESLINT_ROOT);
  }

  private static String nameOf(final Path root, final Path file) {
    return root.relativize(file).toString();
  }

  private static String extension(final String name) {
    return name.substring(name.lastIndexOf('.') + 1);
  }

  private static String notApplied(
      final QualityPreset preset, final String tool, final Path root, final Path file) {
    final String rules = preset == null ? "Ward's " : "the " + preset.id() + " preset's ";
    return rules + tool + " rules are not applied to " + root.relativize(file);
  }
}
