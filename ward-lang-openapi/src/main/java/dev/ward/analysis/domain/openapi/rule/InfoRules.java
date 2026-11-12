package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about the {@code info} object: contact, description and license. */
final class InfoRules {

  private InfoRules() {}

  /** {@code info-contact}: {@code info} has a {@code contact}. */
  static List<OpenApiFinding> contact(final OpenApiDocument document) {
    return info(document)
        .filter(info -> missing(info, "contact"))
        .map(info -> List.of(Findings.at(info, "`info` section must contain `contact` details")))
        .orElse(List.of());
  }

  /** {@code contact-properties}: a {@code contact} has a name, a url and an email. */
  static List<OpenApiFinding> contactProperties(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> contact =
        info(document).flatMap(info -> info.get("contact")).map(document::resolve);
    for (final String property : List.of("name", "url", "email")) {
      contact
          .filter(node -> Findings.text(node, property).isEmpty())
          .ifPresent(
              node ->
                  findings.add(
                      Findings.at(node, "`contact` section must contain a `" + property + "`")));
    }
    return findings;
  }

  /** {@code info-description}: {@code info} has a non-empty {@code description}. */
  static List<OpenApiFinding> description(final OpenApiDocument document) {
    return info(document)
        .filter(info -> Findings.text(info, "description").isEmpty())
        .map(info -> List.of(Findings.at(info, "`info` section must have a `description`")))
        .orElse(List.of());
  }

  /** {@code info-license}: {@code info} has a {@code license} and the license has a name. */
  static List<OpenApiFinding> license(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> info = info(document);
    if (info.isPresent() && missing(info.get(), "license")) {
      findings.add(Findings.at(info.get(), "`info` section must contain a `license`"));
    }
    license(document, info)
        .filter(license -> Findings.text(license, "name").isEmpty())
        .ifPresent(
            license ->
                findings.add(Findings.at(license, "`license` section must contain a `name`")));
    return findings;
  }

  /** {@code license-url}: a {@code license} has a {@code url} or an {@code identifier}. */
  static List<OpenApiFinding> licenseUrl(final OpenApiDocument document) {
    return license(document, info(document))
        .filter(
            license ->
                Findings.text(license, "url").isEmpty()
                    && Findings.text(license, "identifier").isEmpty())
        .map(
            license ->
                List.of(
                    Findings.at(
                        license, "`license` section must contain a `url` or an `identifier`")))
        .orElse(List.of());
  }

  private static Optional<DocNode> info(final OpenApiDocument document) {
    return document.parts().info().map(document::resolve);
  }

  private static Optional<DocNode> license(
      final OpenApiDocument document, final Optional<DocNode> info) {
    return info.flatMap(node -> node.get("license")).map(document::resolve);
  }

  private static boolean missing(final DocNode node, final String key) {
    return node.get(key).filter(child -> child.kind() != DocNode.Kind.NULL).isEmpty();
  }
}
