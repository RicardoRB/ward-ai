package dev.ward.analysis.domain.flow.taint;

import java.util.Collection;

/**
 * Tells whether a method can have a taint summary at all. A method returns taint only when a
 * source entry matches a site of its graph, and passes a parameter to a sink only when {@link
 * TaintSpans#mayReachSink} holds; the entries derived from other summaries only exist once some
 * method has one. So when neither holds for any method, every summary is empty and the rounds that
 * look for them can be skipped.
 */
final class TaintRelevance {
  private TaintRelevance() {}

  /** Reports whether one of the methods can return taint or pass a parameter to a sink. */
  static boolean anySite(final Collection<LocalMethod> methods, final TaintSpec spec) {
    for (final LocalMethod method : methods) {
      if (TaintSpans.hasSource(method.graph(), spec)
          || TaintSpans.mayReachSink(method.graph(), spec)) {
        return true;
      }
    }
    return false;
  }
}
