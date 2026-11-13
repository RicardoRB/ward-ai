package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rules about the security schemes under {@code components}: a type or a field that changed.
 */
final class SecuritySchemeRules {

  private static final String KEY_NAME = "name";

  private SecuritySchemeRules() {}

  /** The security scheme rules. */
  static List<BreakingRule> rules() {
    return List.of(
        scheme("api-security-component-type-changed", "type", "type"),
        scheme("api-security-component-http-scheme-changed", "scheme", "http"),
        scheme("api-security-component-api-key-in-changed", "in", "apiKey"),
        RowRule.of(
            "api-security-component-api-key-name-changed", SecuritySchemeRules::keyNameChanged));
  }

  /** A change of one field of a security scheme of the given type, under {@code components}. */
  private static BreakingRule scheme(final String id, final String field, final String type) {
    return RowRule.of(
        id,
        pair -> {
          final List<BreakingFinding> findings = new ArrayList<>();
          for (final DocNode.Entry before : schemes(pair.base())) {
            schemeOf(pair.revision(), before.key())
                .filter(now -> differs(pair.base().resolve(before.value()), now, field, type))
                .ifPresent(now -> findings.add(finding(id, before.key(), now)));
          }
          return findings;
        });
  }

  private static List<BreakingFinding> keyNameChanged(final DocumentPair pair) {
    final List<BreakingFinding> findings = new ArrayList<>();
    final String id = "api-security-component-api-key-name-changed";
    for (final DocNode.Entry before : schemes(pair.base())) {
      schemeOf(pair.revision(), before.key())
          .filter(
              now ->
                  differs(pair.base().resolve(before.value()), now, KEY_NAME, "apiKey")
                      || differs(pair.base().resolve(before.value()), now, "in", "apiKey"))
          .ifPresent(now -> findings.add(finding(id, before.key(), now)));
    }
    return findings;
  }

  /** Tells whether {@code field} differs; a field of a given type counts only if both have it. */
  private static boolean differs(
      final DocNode before, final DocNode now, final String field, final String type) {
    final boolean applicable =
        "type".equals(type)
            || (type.equals(before.string("type").orElse(""))
                && type.equals(now.string("type").orElse("")));
    return applicable && !before.string(field).equals(now.string(field));
  }

  private static List<DocNode.Entry> schemes(final OpenApiDocument document) {
    return document.parts().component("securitySchemes");
  }

  private static Optional<DocNode> schemeOf(final OpenApiDocument document, final String name) {
    return schemes(document).stream()
        .filter(entry -> entry.key().equals(name))
        .map(entry -> document.resolve(entry.value()))
        .findFirst();
  }

  private static BreakingFinding finding(final String id, final String name, final DocNode at) {
    return new BreakingFinding(
        RowRule.PREFIX + id, "the security scheme " + name + " changed", "", "", at);
  }
}
