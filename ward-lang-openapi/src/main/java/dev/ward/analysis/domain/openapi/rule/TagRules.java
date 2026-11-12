package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The rules about tags: the global list and the tags of the operations. */
final class TagRules {

  private static final String TAGS = "tags";
  private static final String NAME = "name";
  private static final int SINGLE = 1;
  private static final String TAGS_MESSAGE =
      "Top level spec `tags` must not be empty, and must be an array: ";

  private TagRules() {}

  /** {@code openapi-tags}: the top-level {@code tags} is an array of distinct objects. */
  static List<OpenApiFinding> openapiTags(final OpenApiDocument document) {
    final DocNode root = document.root();
    final Optional<DocNode> tags = root.get(TAGS);
    if (tags.isEmpty()) {
      return List.of(
          Findings.in(
              root,
              root.entries().getFirst().keySpan(),
              TAGS_MESSAGE + "`tags`, is missing and is required"));
    }
    final DocNode node = document.resolve(tags.get());
    if (node.kind() != DocNode.Kind.ARRAY) {
      return List.of(
          Findings.atValue(
              node, TAGS_MESSAGE + "got " + DocEquality.typeName(node) + ", want array"));
    }
    return duplicateTags(node);
  }

  /** {@code openapi-tags-alphabetical}: the global tags are sorted by name. */
  static List<OpenApiFinding> alphabetical(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> tags = document.root().get(TAGS).map(document::resolve);
    final List<DocNode> names = tags.map(TagRules::tagNames).orElse(List.of());
    for (int index = 0; index + 1 < names.size(); index++) {
      final int next = nextScalar(names, index);
      if (next < names.size() && names.get(index).text().compareTo(names.get(next).text()) > 0) {
        findings.add(
            Findings.atValue(
                tags.orElseThrow(),
                "Tags must be in alphabetical order: `"
                    + names.get(next).text()
                    + "` must be placed before `"
                    + names.get(index).text()
                    + "` (alphabetical)"));
      }
    }
    return findings;
  }

  /** {@code tag-description}: every global tag has a description. */
  static List<OpenApiFinding> description(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode tag : document.parts().tags()) {
      final DocNode resolved = document.resolve(tag);
      if (Findings.text(resolved, "description").isEmpty()) {
        findings.add(
            Findings.atValue(
                resolved, "tag `" + Findings.text(resolved, NAME) + "` must have a description"));
      }
    }
    return findings;
  }

  /** {@code operation-tags}: every operation has at least one tag. */
  static List<OpenApiFinding> operationTags(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      if (operation.node().get(TAGS).map(DocNode::items).orElse(List.of()).isEmpty()) {
        findings.add(
            Findings.at(
                operation.node(),
                "tags for `"
                    + operation.method().toUpperCase(Locale.ROOT)
                    + "` operation are missing"));
      }
    }
    return findings;
  }

  /** {@code operation-tag-defined}: the tags of an operation are defined as global tags. */
  static List<OpenApiFinding> tagDefined(final OpenApiDocument document) {
    final Set<String> defined = new HashSet<>();
    for (final DocNode tag : document.parts().tags()) {
      defined.add(Findings.text(document.resolve(tag), NAME));
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode tag : operation.node().get(TAGS).map(DocNode::items).orElse(List.of())) {
        if (tag.kind() == DocNode.Kind.STRING && !defined.contains(tag.text())) {
          findings.add(
              Findings.atValue(
                  tag,
                  "tag `"
                      + tag.text()
                      + "` for `"
                      + operation.method().toUpperCase(Locale.ROOT)
                      + "` operation is not defined as a global tag"));
        }
      }
    }
    return findings;
  }

  /** {@code operation-singular-tag}: an operation has at most one tag. */
  static List<OpenApiFinding> singularTag(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final List<DocNode> tags = operation.node().get(TAGS).map(DocNode::items).orElse(List.of());
      if (tags.size() > SINGLE) {
        findings.add(
            Findings.at(
                operation.node().get(TAGS).orElseThrow(),
                "the `"
                    + operation.method()
                    + "` operation at path `"
                    + operation.path()
                    + "` contains more than one tag ("
                    + tags.size()
                    + " is too many)'"));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> duplicateTags(final DocNode tags) {
    final List<DocNode> items = tags.items();
    for (int first = 0; first < items.size(); first++) {
      for (int second = first + 1; second < items.size(); second++) {
        if (DocEquality.same(items.get(first), items.get(second))) {
          return List.of(
              Findings.atValue(
                  tags, TAGS_MESSAGE + "items at " + first + " and " + second + " are equal"));
        }
      }
    }
    return List.of();
  }

  private static List<DocNode> tagNames(final DocNode tags) {
    final List<DocNode> names = new ArrayList<>();
    for (final DocNode tag : tags.items()) {
      names.add(tag.get(NAME).filter(DocNode::isScalar).orElse(null));
    }
    return names;
  }

  /** The index of the next tag after {@code index} that has a scalar name, or the size. */
  private static int nextScalar(final List<DocNode> names, final int index) {
    int next = index + 1;
    while (next < names.size() && (names.get(next) == null || names.get(index) == null)) {
      next++;
    }
    return names.get(index) == null ? names.size() : next;
  }
}
