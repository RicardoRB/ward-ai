package dev.ward.domain.gate;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The lines of one source file that a coverage report marks, by line number.
 *
 * @param coverable the lines that hold executable code
 * @param covered the coverable lines that ran at least once
 */
public record FileLines(SortedSet<Integer> coverable, SortedSet<Integer> covered) {

  /** Copies both sets and keeps only covered lines that are coverable. */
  public FileLines {
    coverable = Collections.unmodifiableSortedSet(new TreeSet<>(coverable));
    final SortedSet<Integer> kept = new TreeSet<>(covered);
    kept.retainAll(coverable);
    covered = Collections.unmodifiableSortedSet(kept);
  }

  /** Returns the lines of both: a line is covered when either marks it covered. */
  public FileLines merge(final FileLines other) {
    final SortedSet<Integer> allCoverable = new TreeSet<>(coverable);
    allCoverable.addAll(other.coverable);
    final SortedSet<Integer> allCovered = new TreeSet<>(covered);
    allCovered.addAll(other.covered);
    return new FileLines(allCoverable, allCovered);
  }

  /** Returns the number of coverable lines from {@code first} to {@code last}, inclusive. */
  public int coverableIn(final int first, final int last) {
    return coverable.subSet(first, last + 1).size();
  }

  /** Returns the number of covered lines from {@code first} to {@code last}, inclusive. */
  public int coveredIn(final int first, final int last) {
    return covered.subSet(first, last + 1).size();
  }
}
