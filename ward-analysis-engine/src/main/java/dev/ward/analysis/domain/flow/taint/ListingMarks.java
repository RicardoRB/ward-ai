package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;

/**
 * Reads the mark that the frontend leaves on a function whose result lists the entries of a
 * directory that start with its one parameter ({@value #PREFIX_LISTING}): the qualifier is the
 * parameter and the annotation the name of the list. A caller that saw the list empty and left,
 * or took the other branch ({@value #LENGTH_CHECKED}), knows that the key is the start of the name
 * of an entry, which holds no path separator. {@link MemberLookups} writes the key clean there.
 */
final class ListingMarks {
  /** The name of the site that marks such a function. */
  static final String PREFIX_LISTING = "<prefix-listing>";

  /** The name of the site that marks the check that the list of a call result is empty. */
  static final String LENGTH_CHECKED = "<length-checked>";

  private ListingMarks() {}

  /** Whether the method lists entries that start with its parameter in the list {@code list}. */
  static boolean vouches(final LocalMethod method, final String list) {
    return method.arity() == 1
        && method.graph().blocks().stream()
            .flatMap(block -> block.sites().stream())
            .anyMatch(site -> marks(method, site, list));
  }

  /** Whether a graph has a check of the list of a call result. */
  static boolean checked(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .flatMap(block -> block.sites().stream())
        .anyMatch(site -> LENGTH_CHECKED.equals(site.name()));
  }

  private static boolean marks(final LocalMethod method, final FlowSite site, final String list) {
    return PREFIX_LISTING.equals(site.name())
        && site.annotations().contains(list)
        && method.parameters().contains(site.qualifier())
        && MemberLookups.assignments(method.graph(), site.qualifier()) == 1;
  }
}
