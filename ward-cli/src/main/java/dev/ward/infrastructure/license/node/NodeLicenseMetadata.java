package dev.ward.infrastructure.license.node;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@code license} of a Node package's {@code package.json} in {@code node_modules}: from
 * the declaring member's directory upward to the project root (workspaces hoist) and in the {@code
 * .pnpm} store. Yarn Plug'n'Play archives are not read, so such a package is "not installed". The
 * file counts only when its {@code version} equals the inventory's.
 */
public final class NodeLicenseMetadata implements LicenseMetadata {
  private static final String NODE_MODULES = "node_modules";
  private static final String PACKAGE_JSON = "package.json";
  private static final String TYPE = "type";

  private final JsonCodec codec;

  /** Creates the reader over the JSON codec. */
  public NodeLicenseMetadata(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public LicenseLookup lookup(
      final Project project, final String modulePath, final Dependency dependency) {
    final String name = dependency.id().name();
    final String version = dependency.id().version();
    String mismatch = null;
    final Path root = project.root().toAbsolutePath().normalize();
    Path directory = root.resolve(modulePath).normalize();
    while (directory != null && directory.startsWith(root)) {
      final Path modules = directory.resolve(NODE_MODULES);
      for (final Path candidate : candidates(modules, name, version)) {
        final Optional<Map<?, ?>> json = read(candidate.resolve(PACKAGE_JSON));
        if (json.isPresent()) {
          if (version.equals(json.get().get("version"))) {
            return declared(json.get());
          }
          mismatch = String.valueOf(json.get().get("version"));
        }
      }
      directory = directory.getParent();
    }
    return mismatch == null
        ? LicenseLookup.missing(
            LicenseLookup.Reason.NOT_INSTALLED, name + " is not installed in node_modules")
        : LicenseLookup.missing(
            LicenseLookup.Reason.VERSION_MISMATCH,
            "installed version " + mismatch + " differs from " + version);
  }

  private static List<Path> candidates(
      final Path modules, final String name, final String version) {
    return List.of(
        modules.resolve(name),
        modules
            .resolve(".pnpm")
            .resolve(name.replace('/', '+') + "@" + version)
            .resolve(NODE_MODULES)
            .resolve(name));
  }

  private Optional<Map<?, ?>> read(final Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      final Object parsed = codec.fromJson(Files.readString(file), Object.class);
      return parsed instanceof Map<?, ?> map ? Optional.of(map) : Optional.empty();
    } catch (IOException | JsonCodecException exception) {
      return Optional.empty();
    }
  }

  private static LicenseLookup declared(final Map<?, ?> json) {
    final List<DeclaredLicense.Entry> entries = new ArrayList<>();
    collect(json.get("license"), entries);
    collect(json.get("licenses"), entries);
    return entries.isEmpty()
        ? LicenseLookup.missing(
            LicenseLookup.Reason.NO_LICENSE_DECLARED, "package.json declares no license")
        : LicenseLookup.found(new DeclaredLicense(entries), "package-json");
  }

  /** Accepts the SPDX string, the legacy {@code {type}} object, and an array of either. */
  private static void collect(final Object value, final List<DeclaredLicense.Entry> entries) {
    if (value instanceof String text && !text.isBlank()) {
      entries.add(new DeclaredLicense.Entry(text, ""));
    } else if (value instanceof Map<?, ?> map && map.get(TYPE) instanceof String type) {
      final Object url = map.get("url");
      entries.add(new DeclaredLicense.Entry(type, url instanceof String text ? text : ""));
    } else if (value instanceof List<?> list) {
      list.forEach(item -> collect(item, entries));
    }
  }
}
