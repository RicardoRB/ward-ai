package dev.ward.infrastructure.license.cargo;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Reads a crate's license from the {@code license} field (an SPDX expression) of its own {@code
 * Cargo.toml}: the copy cargo unpacked in {@code <cargo home>/registry/src/<index>/<name>-<version>},
 * or a vendored copy in {@code vendor/<name>} under the member directory or the project root. A
 * legacy {@code MIT/Apache-2.0} reads as {@code MIT OR Apache-2.0}. A crate that declares no
 * {@code license} is reported as such, never guessed from a {@code license-file}. Ward never runs
 * cargo.
 */
public final class CargoLicenseMetadata implements LicenseMetadata {
  private final Path cargoHome;

  /**
   * Creates the reader.
   *
   * @param cargoHome the directory that holds {@code registry/src}, usually {@code ~/.cargo}
   */
  public CargoLicenseMetadata(final Path cargoHome) {
    this.cargoHome = cargoHome;
  }

  @Override
  public LicenseLookup lookup(
      final Project project,
      final String modulePath,
      final Dependency dependency
  ) {
    final String name = dependency.id().name();
    final String version = dependency.id().version();
    boolean other = false;
    for (final Path manifest : candidates(project.root(), modulePath, name, version)) {
      final TomlParseResult toml = parse(manifest);
      if (toml == null) {
        continue;
      }
      if (version.equals(toml.getString("package.version"))) {
        return declared(toml.getString("package.license"));
      }
      other = true;
    }
    return other
        ? LicenseLookup.missing(
            LicenseLookup.Reason.VERSION_MISMATCH,
            "the vendored version differs from " + version
        )
        : LicenseLookup.missing(
            LicenseLookup.Reason.NOT_INSTALLED,
            name + " " + version + " is not in the cargo registry cache or vendor directory"
        );
  }

  private static LicenseLookup declared(final String license) {
    if (license == null || license.isBlank()) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NO_LICENSE_DECLARED,
          "Cargo.toml declares no license"
      );
    }
    return LicenseLookup.found(DeclaredLicense.of(license.replace("/", " OR ")), "Cargo.toml");
  }

  private List<Path> candidates(
      final Path root,
      final String modulePath,
      final String name,
      final String version
  ) {
    final List<Path> manifests = new ArrayList<>();
    manifests.add(root.resolve(modulePath).resolve("vendor").resolve(name).resolve("Cargo.toml"));
    manifests.add(root.resolve("vendor").resolve(name).resolve("Cargo.toml"));
    final Path registry = cargoHome.resolve("registry").resolve("src");
    if (Files.isDirectory(registry)) {
      try (Stream<Path> indexes = Files.list(registry)) {
        indexes
            .sorted()
            .map(index -> index.resolve(name + "-" + version).resolve("Cargo.toml"))
            .forEach(manifests::add);
      } catch (IOException exception) {
        return manifests.stream().filter(Files::isRegularFile).toList();
      }
    }
    return manifests.stream().filter(Files::isRegularFile).distinct().toList();
  }

  private static TomlParseResult parse(final Path manifest) {
    try {
      final TomlParseResult toml = Toml.parse(Files.readString(manifest));
      return toml.hasErrors() ? null : toml;
    } catch (IOException exception) {
      return null;
    }
  }
}
