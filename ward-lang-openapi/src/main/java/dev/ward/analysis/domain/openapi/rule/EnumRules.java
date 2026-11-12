package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The rules about {@code enum} values of schemas. */
final class EnumRules {

  private static final String ENUM = "enum";
  private static final int TYPE_AND_NULL = 2;

  private EnumRules() {}

  /** {@code duplicated-entry-in-enum}: an enum lists each value once. */
  static List<OpenApiFinding> duplicated(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      final Optional<DocNode> values =
          schema.get(ENUM).filter(node -> node.kind() == DocNode.Kind.ARRAY);
      if (values.isPresent()) {
        for (final String duplicate : duplicates(values.get().items())) {
          findings.add(
              Findings.in(
                  schema,
                  schema.entries().stream()
                      .filter(entry -> ENUM.equals(entry.key()))
                      .findFirst()
                      .orElseThrow()
                      .keySpan(),
                  "enum contains a duplicate: `" + duplicate + "`"));
        }
      }
    }
    return findings;
  }

  /** {@code typed-enum}: every enum value has the type the schema declares. */
  static List<OpenApiFinding> typed(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      final Optional<DocNode> values =
          schema.get(ENUM).filter(node -> node.kind() == DocNode.Kind.ARRAY);
      final String declared = declaredType(schema);
      if (values.isEmpty() || declared.isEmpty()) {
        continue;
      }
      for (final DocNode value : values.get().items()) {
        final String actual = DocEquality.typeName(value);
        if (!"null".equals(actual) && !matches(declared, actual)) {
          findings.add(
              Findings.atValue(
                  values.get(),
                  "enum type mismatch: enum value '"
                      + value.text()
                      + "' is a "
                      + actual
                      + ", but it's defined as a '"
                      + declared
                      + "'"));
        }
      }
    }
    return findings;
  }

  /** {@code nullable-enum-contains-null}: a nullable enum lists {@code null}. */
  static List<OpenApiFinding> nullable(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      final Optional<DocNode> values =
          schema.get(ENUM).filter(node -> node.kind() == DocNode.Kind.ARRAY);
      if (values.isPresent()
          && isNullable(schema)
          && values.get().items().stream().noneMatch(item -> item.kind() == DocNode.Kind.NULL)) {
        findings.add(
            Findings.atValue(
                schema,
                "enum is defined as nullable but does not contain a `null` value. Nullable enums"
                    + " must explicitly include `null` in the enum array (not the string \"null\")"));
      }
    }
    return findings;
  }

  private static Set<String> duplicates(final List<DocNode> items) {
    final Set<String> seen = new LinkedHashSet<>();
    final Set<String> duplicates = new LinkedHashSet<>();
    for (final DocNode item : items) {
      if (!seen.add(item.kind() + ":" + item.text())) {
        duplicates.add(item.text());
      }
    }
    return duplicates;
  }

  /** The declared type, or the one non-null type of a type list, or empty. */
  private static String declaredType(final DocNode schema) {
    final Optional<DocNode> type = schema.get("type");
    if (type.isEmpty()) {
      return "";
    }
    if (type.get().kind() == DocNode.Kind.STRING) {
      return type.get().text();
    }
    String declared = "";
    if (type.get().items().size() == TYPE_AND_NULL) {
      for (final DocNode item : type.get().items()) {
        declared = "null".equals(item.text()) ? declared : item.text();
      }
    }
    return declared;
  }

  private static boolean matches(final String declared, final String actual) {
    return declared.equals(actual) || "number".equals(declared) && "integer".equals(actual);
  }

  private static boolean isNullable(final DocNode schema) {
    final boolean flag =
        schema.get("nullable").map(node -> "true".equals(node.text())).orElse(false);
    return flag
        || schema.get("type").stream()
            .flatMap(type -> type.items().stream())
            .anyMatch(item -> "null".equals(item.text()));
  }
}
