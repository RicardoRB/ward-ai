package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;

/**
 * A breaking change of one schema against its base.
 *
 * @param kind the check id without its location, such as {@code max-set}
 * @param at the node of the revision the finding points at
 */
record SchemaChange(String kind, DocNode at) {}
