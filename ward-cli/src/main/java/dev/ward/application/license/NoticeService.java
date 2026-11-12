package dev.ward.application.license;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.license.LicenseExpression;
import dev.ward.domain.license.LicenseTexts;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Writes the third-party license notice: each package of the inventory under its normalized
 * license, then the full text of each distinct license used, from the texts that Ward bundles.
 * Plain text, sorted, without timestamps, so the same inputs give the same bytes.
 */
public final class NoticeService {
  private static final int INITIAL_CAPACITY = 4096;
  private static final String UNKNOWN = "Unknown license";
  private static final String RULE =
      "================================================================";

  private final ProjectDetector detector;
  private final LicenseService licenses;
  private final LicenseTexts texts;

  /** Creates the service. */
  @Inject
  public NoticeService(
      final ProjectDetector detector, final LicenseService licenses, final LicenseTexts texts) {
    this.detector = detector;
    this.licenses = licenses;
    this.texts = texts;
  }

  /**
   * Returns the notice of the project in {@code directory}.
   *
   * @param scopes the lower-case scope names to include; empty means every scope
   * @throws dev.ward.domain.config.WardException if there is no project or its inventory fails
   */
  public Document generate(final Path directory, final Set<String> scopes) {
    final Project project = Projects.detect(detector, directory);
    final List<PackageLicense> all = licenses.licenses(project, scopes);
    final Map<String, List<String>> byLicense =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    entry -> entry.expression().map(LicenseExpression::render).orElse(UNKNOWN),
                    TreeMap::new,
                    Collectors.mapping(NoticeService::label, Collectors.toList())));
    final Set<String> used =
        all.stream()
            .flatMap(entry -> entry.expression().stream())
            .flatMap(expression -> expression.identifiers().stream())
            .collect(Collectors.toCollection(TreeSet::new));
    final List<String> warnings =
        all.stream()
            .filter(entry -> !entry.known())
            .map(
                entry ->
                    "ward licenses: "
                        + entry.dependency().purl()
                        + " has an unknown license: "
                        + entry.unknownReason())
            .toList();
    return new Document(render(all.size(), byLicense, used), warnings);
  }

  private static String label(final PackageLicense entry) {
    final PackageId id = entry.dependency().id();
    return id.name() + ":" + id.version() + " [" + id.ecosystem().purlType() + "]";
  }

  private String render(
      final int count, final Map<String, List<String>> byLicense, final Set<String> used) {
    if (count == 0) {
      return "The project has no dependencies.\n";
    }
    final StringBuilder out = new StringBuilder(INITIAL_CAPACITY);
    out.append("Lists of ").append(count).append(" third-party dependencies.\n");
    byLicense.forEach(
        (license, packages) -> {
          out.append('\n').append(license).append('\n');
          packages.stream().sorted().forEach(name -> out.append("    ").append(name).append('\n'));
        });
    for (final String id : used) {
      out.append('\n')
          .append(RULE)
          .append('\n')
          .append(id)
          .append('\n')
          .append(RULE)
          .append("\n\n");
      final Optional<String> text = texts.text(id);
      out.append(
              text.map(NoticeService::trimmed)
                  .orElse("The text of " + id + " is not bundled with Ward."))
          .append('\n');
    }
    return out.toString();
  }

  private static String trimmed(final String text) {
    return text.strip();
  }
}
