package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The taint of one write: the source it came from, the steps from that source to the write, and
 * the steps on the way that the analysis assumed rather than proved.
 *
 * @param source the name of the source
 * @param path the steps up to and including this write, in order
 * @param assumptions why the flow is not proven, sorted and without repeats; empty when it is
 */
record Taint(String source, List<FlowStep> path, List<String> assumptions) {
  /**
   * Copies the path and sorts the assumptions.
   *
   * @throws NullPointerException if a field is missing
   */
  Taint {
    Objects.requireNonNull(source, "source");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    assumptions = List.copyOf(new TreeSet<>(Objects.requireNonNull(assumptions, "assumptions")));
  }

  /** A taint whose flow is proven. */
  Taint(final String source, final List<FlowStep> path) {
    this(source, path, List.of());
  }

  /** Reports whether every step of the flow was resolved. */
  boolean proven() {
    return assumptions.isEmpty();
  }

  /** Returns this taint with more assumptions. */
  Taint assuming(final List<String> more) {
    if (more.isEmpty()) {
      return this;
    }
    final Set<String> all = new TreeSet<>(assumptions);
    all.addAll(more);
    return new Taint(source, path, List.copyOf(all));
  }

  /** Returns this taint with a longer path and the given extra assumptions. */
  Taint extended(final FlowStep step, final List<String> more) {
    final List<FlowStep> longer = new ArrayList<>(path);
    longer.add(step);
    return new Taint(source, longer, assumptions).assuming(more);
  }

  /** The better of two taints of one place: a proven one before an assumed one, else the first. */
  static Taint better(final Taint first, final Taint second) {
    return first.proven() || !second.proven() ? first : second;
  }
}
