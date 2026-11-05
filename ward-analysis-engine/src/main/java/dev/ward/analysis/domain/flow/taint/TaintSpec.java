package dev.ward.analysis.domain.flow.taint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The sources, sinks, sanitizers and propagators of one or more taint specification files.
 *
 * @param sources the entries that introduce untrusted values
 * @param sinks the entries that must not receive untrusted values in their guarded arguments
 * @param sanitizers the entries whose results are clean
 * @param propagators the entries that carry taint to their results
 */
public record TaintSpec(
    List<TaintEntry> sources,
    List<TaintEntry> sinks,
    List<TaintEntry> sanitizers,
    List<TaintEntry> propagators) {

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing or holds a null element
   */
  public TaintSpec {
    sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    sinks = List.copyOf(Objects.requireNonNull(sinks, "sinks"));
    sanitizers = List.copyOf(Objects.requireNonNull(sanitizers, "sanitizers"));
    propagators = List.copyOf(Objects.requireNonNull(propagators, "propagators"));
  }

  /**
   * Joins specifications, keeping the entries of each in file order.
   *
   * @param specs the specifications to join
   * @return one specification with the entries of all of them
   */
  public static TaintSpec combine(final List<TaintSpec> specs) {
    final List<TaintEntry> sources = new ArrayList<>();
    final List<TaintEntry> sinks = new ArrayList<>();
    final List<TaintEntry> sanitizers = new ArrayList<>();
    final List<TaintEntry> propagators = new ArrayList<>();
    for (final TaintSpec spec : specs) {
      sources.addAll(spec.sources());
      sinks.addAll(spec.sinks());
      sanitizers.addAll(spec.sanitizers());
      propagators.addAll(spec.propagators());
    }
    return new TaintSpec(sources, sinks, sanitizers, propagators);
  }
}
