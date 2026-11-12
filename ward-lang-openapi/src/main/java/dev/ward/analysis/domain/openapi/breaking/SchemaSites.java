package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The places where an operation has a schema in both documents. */
final class SchemaSites {

  /** A schema of the base and the one at the same place of the revision, in an operation. */
  record Site(Operation operation, DocNode base, DocNode revision) {}

  private SchemaSites() {}

  /** The request body schemas of the media types both documents have. */
  static List<Site> requestBodies(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final Optional<DocNode> before = Bodies.request(pair.base(), base);
          final Optional<DocNode> after = Bodies.request(pair.revision(), now);
          return before.isPresent() && after.isPresent()
              ? bodies(pair, now, before.get(), after.get())
              : List.of();
        });
  }

  /** The response body schemas of the status codes and media types both documents have. */
  static List<Site> responseBodies(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final List<Site> sites = new ArrayList<>();
          responses(pair, base, now)
              .forEach(
                  response ->
                      sites.addAll(bodies(pair, now, response.base(), response.revision())));
          return sites;
        });
  }

  /** The schemas of the parameters both documents have, by place and name. */
  static List<Site> parameters(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final Map<String, DocNode> after = NamedParts.parameters(pair.revision(), now);
          final List<Site> sites = new ArrayList<>();
          NamedParts.parameters(pair.base(), base)
              .forEach(
                  (key, before) ->
                      schemas(pair, before, Optional.ofNullable(after.get(key)))
                          .ifPresent(schemas -> sites.add(site(now, schemas))));
          return sites;
        });
  }

  /** The schemas of the response headers both documents have. */
  static List<Site> headers(final DocumentPair pair) {
    return OperationPairs.each(
        pair,
        (base, now) -> {
          final List<Site> sites = new ArrayList<>();
          for (final Responses response : responses(pair, base, now)) {
            for (final DocNode.Entry header :
                NamedParts.entries(pair.base(), response.base(), "headers")) {
              final Optional<DocNode> other =
                  NamedParts.entry(pair.revision(), response.revision(), "headers", header.key());
              schemas(pair, header.value(), other)
                  .ifPresent(schemas -> sites.add(site(now, schemas)));
            }
          }
          return sites;
        });
  }

  private record Responses(DocNode base, DocNode revision) {}

  private static List<Responses> responses(
      final DocumentPair pair, final Operation base, final Operation now) {
    final List<Responses> responses = new ArrayList<>();
    for (final DocNode.Entry code : NamedParts.entries(pair.base(), base.node(), "responses")) {
      NamedParts.entry(pair.revision(), now.node(), "responses", code.key())
          .ifPresent(
              other ->
                  responses.add(
                      new Responses(
                          pair.base().resolve(code.value()), pair.revision().resolve(other))));
    }
    return responses;
  }

  private static List<Site> bodies(
      final DocumentPair pair, final Operation now, final DocNode before, final DocNode after) {
    final List<Site> sites = new ArrayList<>();
    for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), before)) {
      final Optional<DocNode> other = Bodies.mediaType(pair.revision(), after, type.key());
      if (other.isPresent()) {
        schemas(pair, type.value(), other).ifPresent(schemas -> sites.add(site(now, schemas)));
      }
    }
    return sites;
  }

  private record Schemas(DocNode base, DocNode revision) {}

  /** The schemas of two nodes that each have a {@code schema}, with references followed. */
  private static Optional<Schemas> schemas(
      final DocumentPair pair, final DocNode before, final Optional<DocNode> after) {
    if (after.isEmpty()) {
      return Optional.empty();
    }
    final Optional<DocNode> first = Bodies.schema(pair.base(), pair.base().resolve(before));
    final Optional<DocNode> second =
        Bodies.schema(pair.revision(), pair.revision().resolve(after.get()));
    return first.isPresent() && second.isPresent()
        ? Optional.of(new Schemas(first.get(), second.get()))
        : Optional.empty();
  }

  private static Site site(final Operation now, final Schemas schemas) {
    return new Site(now, schemas.base(), schemas.revision());
  }
}
