package dev.ward.infrastructure.project;

import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

/** Reads {@code package.json} leniently, as project detection does. */
public final class FileSystemPackageManifests implements PackageManifests {

  /** Creates the adapter. */
  @Inject
  public FileSystemPackageManifests() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public PackageManifest read(final Path root) {
    final Map<?, ?> manifest = NodeProjectInspection.manifest(root);
    final Map<String, String> dependencies = new LinkedHashMap<>();
    declared(manifest.get("dependencies"), dependencies);
    declared(manifest.get("devDependencies"), dependencies);
    return new PackageManifest(
        dependencies,
        text(manifest.get("type")),
        text(manifest.get("packageManager")),
        text(scripts(manifest).get("prepare")));
  }

  private static Map<?, ?> scripts(final Map<?, ?> manifest) {
    return manifest.get("scripts") instanceof Map<?, ?> scripts ? scripts : Map.of();
  }

  private static void declared(final Object section, final Map<String, String> into) {
    if (section instanceof Map<?, ?> entries) {
      entries.forEach(
          (name, version) -> {
            if (name instanceof String key && version instanceof String range) {
              into.put(key, range);
            }
          });
    }
  }

  private static String text(final Object value) {
    return value instanceof String string ? string : null;
  }
}
