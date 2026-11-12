package dev.ward.analysis.domain.openapi.breaking;

import java.util.List;

/**
 * One breaking-change check: the findings of one oasdiff error-level check between two documents.
 */
public interface BreakingRule {

  /** Returns the rule id, {@code ward:openapi/<oasdiff check id>}. */
  String id();

  /** Returns the findings of this rule in {@code pair}, in the order of the base document. */
  List<BreakingFinding> check(DocumentPair pair);
}
