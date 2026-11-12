package dev.ward.application.license;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.license.LicenseCatalog;
import dev.ward.domain.license.LicenseExpression;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.license.LicenseNormalizer;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * The license lookup that the {@code license} gate, {@code ward sbom} and {@code ward licenses}
 * share: takes the packages of the dependency inventory, finds each one's declared license in local
 * metadata and normalizes it to an SPDX expression. Never touches the network. An inventory that
 * cannot be produced is a {@link dev.ward.domain.dependency.DependencyInventoryException}, never an
 * empty result.
 */
public final class LicenseService {
  private static final Logger LOGGER = Logger.getLogger(LicenseService.class.getName());

  private final DependencyInventoryService inventory;
  private final Map<Ecosystem, LicenseMetadata> readers;
  private final LicenseNormalizer normalizer;

  /**
   * Creates the service over the inventory, one metadata reader per ecosystem, and the SPDX data.
   */
  @Inject
  public LicenseService(
      final DependencyInventoryService inventory,
      final Map<Ecosystem, LicenseMetadata> readers,
      final LicenseCatalog catalog) {
    this.inventory = inventory;
    this.readers = Map.copyOf(readers);
    this.normalizer = new LicenseNormalizer(catalog);
  }

  /**
   * Returns the license of every distinct package of {@code project} whose scope is in {@code
   * scopes}, ordered by package.
   *
   * @param scopes the lower-case inventory scope names to include; empty means every scope
   * @throws dev.ward.domain.dependency.DependencyInventoryException if the inventory fails
   */
  public List<PackageLicense> licenses(final Project project, final Set<String> scopes) {
    final DependencyInventory resolved = inventory.inventory(project);
    final Set<PackageId> seen = new HashSet<>();
    final List<PackageLicense> result = new ArrayList<>();
    for (final ModuleDependencies module : resolved.modules()) {
      for (final Dependency dependency : module.dependencies()) {
        if (included(dependency, scopes) && seen.add(dependency.id())) {
          result.add(resolve(project, module.modulePath(), dependency));
        }
      }
    }
    result.sort((first, second) -> first.dependency().id().compareTo(second.dependency().id()));
    return List.copyOf(result);
  }

  private static boolean included(final Dependency dependency, final Set<String> scopes) {
    return scopes.isEmpty() || scopes.contains(dependency.scope().name().toLowerCase(Locale.ROOT));
  }

  private PackageLicense resolve(
      final Project project, final String modulePath, final Dependency dependency) {
    final LicenseMetadata reader = readers.get(dependency.id().ecosystem());
    final LicenseLookup lookup =
        reader == null
            ? LicenseLookup.missing(LicenseLookup.Reason.NOT_INSTALLED, "no license reader")
            : reader.lookup(project, modulePath, dependency);
    final Optional<LicenseExpression> expression =
        lookup.present() ? normalizer.normalize(lookup.license()) : Optional.empty();
    if (expression.isPresent()) {
      LOGGER.log(
          Level.FINE,
          () ->
              "event=license-resolved ecosystem="
                  + dependency.id().ecosystem().purlType()
                  + " source="
                  + lookup.detail());
    } else {
      LOGGER.log(
          Level.FINE,
          () ->
              "event=license-unknown ecosystem="
                  + dependency.id().ecosystem().purlType()
                  + " reason="
                  + (lookup.present() ? "unrecognized" : lookup.reason()));
    }
    return new PackageLicense(dependency, lookup, expression);
  }
}
