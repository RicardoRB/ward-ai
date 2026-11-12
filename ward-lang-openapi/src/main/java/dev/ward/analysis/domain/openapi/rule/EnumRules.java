package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about {@code enum}: no repeated value, and every value of the declared type. */
final class EnumRules {

  private static final String ENUM = "enum";
  private static final String TYPE = "type";
  private static final String PROPERTIES = "properties";

  private EnumRules() {}

  /** {@code duplicated-entry-in-enum}: an {@code enum} lists no value twice. */
  static List<OpenApiFinding> duplicated(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final Optional<DocNode> values = located.node().get(ENUM);
      if (!PROPERTIES.equals(located.key())
          && values.isPresent()
          && values.get().kind() == DocNode.Kind.ARRAY) {
        duplicate(values.get())
            .ifPresent(
                pair ->
                    findings.add(
                        Findings.atValue(
                            values.get(),
                            "\"enum\" property must not have duplicate items (items ## "
                                + pair
                                + " are identical)")));
      }
    }
    return findings;
  }

  /** The items {@code "j and i"} ajv names for the first repeat, searching from the last item. */
  private static Optional<String> duplicate(final DocNode values) {
    final List<DocNode> items = values.items();
    for (int i = items.size() - 1; i > 0; i--) {
      final int j = earlier(items, i);
      if (j >= 0) {
        return Optional.of(j + " and " + i);
      }
    }
    return Optional.empty();
  }

  private static int earlier(final List<DocNode> items, final int i) {
    for (int j = i - 1; j >= 0; j--) {
      if (DocEquality.same(items.get(i), items.get(j))) {
        return j;
      }
    }
    return -1;
  }

  /** {@code typed-enum}: every value of an {@code enum} has the declared {@code type}. */
  static List<OpenApiFinding> typed(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final DocNode node = located.node();
      final Optional<DocNode> values = node.get(ENUM);
      final Optional<List<String>> types = types(document, node);
      if (values.isPresent() && values.get().kind() == DocNode.Kind.ARRAY && types.isPresent()) {
        findings.addAll(mismatches(values.get(), types.get()));
      }
    }
    return findings;
  }

  /** The declared types of a schema, with {@code null} added by {@code nullable} in 3.0. */
  private static Optional<List<String>> types(final OpenApiDocument document, final DocNode node) {
    final Optional<DocNode> type = node.get(TYPE);
    if (type.isEmpty()) {
      return Optional.empty();
    }
    final List<String> types = new ArrayList<>();
    if (type.get().kind() == DocNode.Kind.STRING) {
      types.add(type.get().text());
    } else if (type.get().kind() == DocNode.Kind.ARRAY
        && type.get().items().stream().allMatch(item -> item.kind() == DocNode.Kind.STRING)) {
      type.get().items().forEach(item -> types.add(item.text()));
    } else {
      return Optional.empty();
    }
    final boolean nullable =
        node.get("nullable").filter(flag -> "true".equals(flag.text())).isPresent();
    if (nullable && !document.isVersion31()) {
      types.add("null");
    }
    return Optional.of(types);
  }

  private static List<OpenApiFinding> mismatches(final DocNode values, final List<String> types) {
    final boolean integer = types.contains("integer");
    final String shown = String.join(",", types);
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode value : values.items()) {
      if (!types.contains(JavaScript.typeOf(value, integer))) {
        findings.add(
            Findings.atValue(
                value, "Enum value " + JavaScript.print(value) + " must be \"" + shown + "\"."));
      }
    }
    return findings;
  }
}
