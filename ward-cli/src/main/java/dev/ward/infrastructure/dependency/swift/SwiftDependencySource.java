package dev.ward.infrastructure.dependency.swift;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Reads the inventory of a SwiftPM or Xcode project from {@code Package.resolved}, without running
 * a package manager. The pins are the packages; the repository locations that {@code Package.swift}
 * or the Xcode project names are the direct ones. Every package is a runtime package.
 */
public final class SwiftDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(SwiftDependencySource.class.getName());
  private static final String RESOLVED = "Package.resolved";
  private static final String PACKAGE_SWIFT = "Package.swift";
  private static final String SHARED = "xcshareddata/swiftpm/" + RESOLVED;

  private final JsonCodec codec;

  /** Creates the source; Dagger injects the codec. */
  @Inject
  public SwiftDependencySource(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public DependencyInventory read(final Project project) {
    final Path root = project.root();
    final boolean xcode = project.buildTool() == BuildTool.XCODE;
    final Declared declared = xcode ? xcode(project) : swiftPm(project);
    final Optional<Path> resolved = xcode ? xcodeResolved(root) : swiftPmResolved(root);
    if (resolved.isEmpty()) {
      if (!declared.locations().isEmpty()) {
        throw new DependencyInventoryException(
            root.toString(),
            RESOLVED,
            "the project declares packages and has no Package.resolved",
            xcode ? "run `xcodebuild -resolvePackageDependencies`" : "run `swift package resolve`");
      }
      return new DependencyInventory(List.of(new ModuleDependencies("", List.of())));
    }
    final String label = LockFiles.relative(root, resolved.get());
    final List<PackageResolved.Pin> pins = pins(project, resolved.get(), label);
    final ModuleBuilder builder = new ModuleBuilder(label, false);
    pins.forEach(
        pin -> builder.add(pin.id(), pin.released()).scope(pin.id(), DependencyScope.RUNTIME));
    declared
        .locations()
        .forEach(
            location ->
                builder.direct(
                    pinFor(project, pins, label, declared, location),
                    DependencyScope.RUNTIME,
                    declared.file()));
    final ModuleDependencies module = builder.build("");
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source source="
                + label
                + " modules=1 entries="
                + module.dependencies().size());
    return new DependencyInventory(List.of(module));
  }

  private static PackageId pinFor(
      final Project project,
      final List<PackageResolved.Pin> pins,
      final String label,
      final Declared declared,
      final String location) {
    return pins.stream()
        .filter(pin -> pin.id().name().equals(location) || pin.identity().equals(location))
        .map(PackageResolved.Pin::id)
        .findFirst()
        .orElseThrow(
            () ->
                new DependencyInventoryException(
                    project.root().toString(),
                    label,
                    "Package.resolved has no pin for "
                        + location
                        + " declared in "
                        + declared.file(),
                    project.buildTool() == BuildTool.XCODE
                        ? "run `xcodebuild -resolvePackageDependencies`"
                        : "run `swift package resolve`"));
  }

  /** The locations a manifest names and the file that names them. */
  private record Declared(Set<String> locations, String file) {}

  private Declared swiftPm(final Project project) {
    final Path file = project.root().resolve(PACKAGE_SWIFT);
    if (!Files.isRegularFile(file)) {
      return new Declared(Set.of(), PACKAGE_SWIFT);
    }
    try {
      return new Declared(SwiftManifest.packageSwift(Files.readString(file)), PACKAGE_SWIFT);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          PACKAGE_SWIFT,
          "it cannot be read",
          "check its permissions",
          e);
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          PACKAGE_SWIFT,
          e.getMessage(),
          "write each .package url as a string literal",
          e);
    }
  }

  private Declared xcode(final Project project) {
    final Set<String> locations = new LinkedHashSet<>();
    String declaringFile = "project.pbxproj";
    for (final Path pbxproj : children(project.root(), ".xcodeproj")) {
      final Path file = pbxproj.resolve("project.pbxproj");
      if (Files.isRegularFile(file)) {
        declaringFile = LockFiles.relative(project.root(), file);
        try {
          locations.addAll(SwiftManifest.pbxproj(Files.readString(file)));
        } catch (IOException | UncheckedIOException e) {
          throw new DependencyInventoryException(
              project.root().toString(),
              declaringFile,
              "it cannot be read",
              "check its permissions",
              e);
        }
      }
    }
    return new Declared(locations, declaringFile);
  }

  private List<PackageResolved.Pin> pins(
      final Project project, final Path file, final String label) {
    try {
      return PackageResolved.read(Files.readString(file), codec);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, "it cannot be read", "check its permissions", e);
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, e.getMessage(), "run `swift package resolve`", e);
    }
  }

  private static Optional<Path> swiftPmResolved(final Path root) {
    final Path file = root.resolve(RESOLVED);
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  private static Optional<Path> xcodeResolved(final Path root) {
    final List<Path> candidates = new ArrayList<>();
    children(root, ".xcworkspace").forEach(dir -> candidates.add(dir.resolve(SHARED)));
    children(root, ".xcodeproj")
        .forEach(dir -> candidates.add(dir.resolve("project.xcworkspace").resolve(SHARED)));
    return candidates.stream().filter(Files::isRegularFile).findFirst();
  }

  private static List<Path> children(final Path root, final String suffix) {
    try (Stream<Path> files = Files.list(root)) {
      return files
          .filter(Files::isDirectory)
          .filter(dir -> LockFiles.fileName(dir).endsWith(suffix))
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }
}
