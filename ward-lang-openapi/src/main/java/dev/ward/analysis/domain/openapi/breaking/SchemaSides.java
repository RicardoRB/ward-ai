package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.Optional;

/**
 * The two documents a schema pair comes from, and the direction of the comparison.
 *
 * @param base the base document
 * @param revision the revised document
 * @param direction which way a change hurts
 */
record SchemaSides(OpenApiDocument base, OpenApiDocument revision, Direction direction) {

  /** The child of a base schema at {@code key}, with references followed. */
  Optional<DocNode> before(final DocNode schema, final String key) {
    return schema.get(key).map(base::resolve);
  }

  /** The child of a revised schema at {@code key}, with references followed. */
  Optional<DocNode> after(final DocNode schema, final String key) {
    return schema.get(key).map(revision::resolve);
  }

  /** Tells whether the request direction is compared. */
  boolean request() {
    return direction == Direction.REQUEST;
  }
}
