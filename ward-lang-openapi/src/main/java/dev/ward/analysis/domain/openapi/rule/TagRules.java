package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.text.Collator;
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

  private TagRules() {}

  /** {@code openapi-tags}: the top-level {@code tags} is a non-empty array. */
  static List<OpenApiFinding> openapiTags(final OpenApiDocument document) {
    return nonEmptyArray(
        document, document.root(), "OpenAPI object must have non-empty \"tags\" array.");
  }

  /** {@code operation-tags}: every operation has a non-empty {@code tags} array. */
  static List<OpenApiFinding> operationTags(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      findings.addAll(
          nonEmptyArray(
              document, operation.node(), "Operation must have non-empty \"tags\" array."));
    }
    return findings;
  }

  /** Spectral's schema {@code {type: array, minItems: 1}} on the {@code tags} of {@code owner}. */
  private static List<OpenApiFinding> nonEmptyArray(
      final OpenApiDocument document, final DocNode owner, final String message) {
    final Optional<DocNode> tags = document.get(owner, TAGS, View.RESOLVED);
    if (tags.isEmpty()) {
      return List.of(Findings.atValue(document.resolve(owner), message));
    }
    if (tags.get().kind() != DocNode.Kind.ARRAY || tags.get().items().isEmpty()) {
      return List.of(Findings.atValue(tags.get(), message));
    }
    return List.of();
  }

  /** {@code openapi-tags-alphabetical}: the global tags are sorted by name. */
  static List<OpenApiFinding> alphabetical(final OpenApiDocument document) {
    final Optional<DocNode> tags = document.get(document.root(), TAGS, View.RESOLVED);
    if (tags.isEmpty()
        || tags.get().kind() != DocNode.Kind.ARRAY
        || tags.get().items().size() < 2) {
      return List.of();
    }
    final List<String> names = new ArrayList<>();
    for (final DocNode item : tags.get().items()) {
      final DocNode tag = document.resolve(item);
      if (tag.kind() != DocNode.Kind.OBJECT) {
        return List.of();
      }
      names.add(tag.get(NAME).map(DocNode::text).orElse(""));
    }
    final Collator collator = Collator.getInstance(Locale.ENGLISH);
    for (int index = 0; index + 1 < names.size(); index++) {
      if (collator.compare(names.get(index), names.get(index + 1)) > 0) {
        return List.of(
            Findings.atValue(tags.get(), "OpenAPI object must have alphabetical \"tags\"."));
      }
    }
    return List.of();
  }

  /** {@code openapi-tags-uniqueness}: no two global tags have the same name. */
  static List<OpenApiFinding> uniqueness(final OpenApiDocument document) {
    final Optional<DocNode> tags = document.get(document.root(), TAGS, View.RESOLVED);
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (tags.isEmpty() || tags.get().kind() != DocNode.Kind.ARRAY) {
      return findings;
    }
    final List<DocNode> names = new ArrayList<>();
    for (final DocNode item : tags.get().items()) {
      final Optional<DocNode> name = document.get(item, NAME, View.RESOLVED);
      if (name.isEmpty() || name.get().kind() != DocNode.Kind.STRING) {
        return List.of();
      }
      names.add(name.get());
    }
    final Set<String> seen = new HashSet<>();
    for (final DocNode name : names) {
      if (!seen.add(name.text())) {
        findings.add(
            Findings.atValue(
                name, "\"tags\" object contains duplicate tag name \"" + name.text() + "\"."));
      }
    }
    return findings;
  }

  /** {@code tag-description}: every global tag has a description. */
  static List<OpenApiFinding> description(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode tag : document.parts().tags()) {
      findings.addAll(
          Fields.requireTruthy(
              document, tag, "Tag object must have \"description\".", "description"));
    }
    return findings;
  }

  /** {@code operation-singular-tag}: no operation has more than one tag. */
  static List<OpenApiFinding> singularTag(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      document
          .get(operation.node(), TAGS, View.RESOLVED)
          .filter(tags -> tags.kind() == DocNode.Kind.ARRAY && tags.items().size() > SINGLE)
          .ifPresent(
              tags ->
                  findings.add(
                      Findings.atValue(tags, "Operation must not have more than a single tag.")));
    }
    return findings;
  }

  /** {@code operation-tag-defined}: every operation tag is one of the global tags. */
  static List<OpenApiFinding> tagDefined(final OpenApiDocument document) {
    final Set<String> defined = new HashSet<>();
    for (final DocNode item : document.parts().tags()) {
      document
          .get(item, NAME, View.RESOLVED)
          .filter(name -> name.kind() == DocNode.Kind.STRING)
          .ifPresent(name -> defined.add(name.text()));
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode tag : tagsOf(document, operation)) {
        if (tag.kind() != DocNode.Kind.STRING || !defined.contains(tag.text())) {
          findings.add(Findings.atValue(tag, "Operation tags must be defined in global tags."));
        }
      }
    }
    return findings;
  }

  private static List<DocNode> tagsOf(final OpenApiDocument document, final Operation operation) {
    return document
        .get(operation.node(), TAGS, View.RESOLVED)
        .filter(tags -> tags.kind() == DocNode.Kind.ARRAY)
        .map(DocNode::items)
        .orElse(List.of());
  }
}
