package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;

/** The rules about the {@code info} object: contact, description and license. */
final class InfoRules {

  private static final String INFO = "info";
  private static final String CONTACT = "contact";
  private static final String LICENSE = "license";

  private InfoRules() {}

  /** {@code info-contact}: {@code info.contact} is present. */
  static List<OpenApiFinding> contact(final OpenApiDocument document) {
    return Fields.requireTruthy(
        document, document.root(), "Info object must have \"contact\" object.", INFO, CONTACT);
  }

  /** {@code contact-properties}: a {@code contact} has a name, a url and an email. */
  static List<OpenApiFinding> contactProperties(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final String message = "Contact object must have \"name\", \"url\" and \"email\".";
    Fields.at(document, document.root(), INFO, CONTACT)
        .ifPresent(
            contact -> {
              for (final String property : List.of("name", "url", "email")) {
                findings.addAll(Fields.requireTruthy(document, contact, message, property));
              }
            });
    return Findings.distinct(findings);
  }

  /** {@code info-description}: {@code info.description} is a non-empty string. */
  static List<OpenApiFinding> description(final OpenApiDocument document) {
    return Fields.requireTruthy(
        document,
        document.root(),
        "Info \"description\" must be present and non-empty string.",
        INFO,
        "description");
  }

  /** {@code info-license}: {@code info.license} is present. */
  static List<OpenApiFinding> license(final OpenApiDocument document) {
    return Fields.requireTruthy(
        document, document.root(), "Info object must have \"license\" object.", INFO, LICENSE);
  }

  /** {@code license-url}: {@code info.license.url} is present. */
  static List<OpenApiFinding> licenseUrl(final OpenApiDocument document) {
    return Fields.requireTruthy(
        document, document.root(), "License object must include \"url\".", INFO, LICENSE, "url");
  }

  /** Tells whether the node is an object, for rules that read a section that may be a string. */
  static boolean isObject(final DocNode node) {
    return node.kind() == DocNode.Kind.OBJECT;
  }
}
