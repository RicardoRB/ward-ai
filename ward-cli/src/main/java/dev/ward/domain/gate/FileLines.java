package dev.ward.domain.gate;

import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The lines of one source file that a coverage report marks, by line number.
 *
 * @param coverable the lines that hold executable code
 * @param covered the coverable lines that ran at least once
 * @param branches the number of branches of each line that has some
 * @param coveredBranches the number of those branches that ran, by line
 */
public record FileLines(
    SortedSet<Integer> coverable,
    SortedSet<Integer> covered,
    SortedMap<Integer, Integer> branches,
    SortedMap<Integer, Integer> coveredBranches) {

  /** Copies both sets and keeps only covered lines that are coverable. */
  public FileLines {
    coverable = Collections.unmodifiableSortedSet(new TreeSet<>(coverable));
    final SortedSet<Integer> kept = new TreeSet<>(covered);
    kept.retainAll(coverable);
    covered = Collections.unmodifiableSortedSet(kept);
    branches = Collections.unmodifiableSortedMap(new TreeMap<>(branches));
    coveredBranches = Collections.unmodifiableSortedMap(new TreeMap<>(coveredBranches));
  }

  /** Creates the lines of a report that records no branches. */
  public FileLines(final SortedSet<Integer> coverable, final SortedSet<Integer> covered) {
    this(coverable, covered, new TreeMap<>(), new TreeMap<>());
  }

  /** Returns the lines of both: a line is covered when either marks it covered. */
  public FileLines merge(final FileLines other) {
    final SortedSet<Integer> allCoverable = new TreeSet<>(coverable);
    allCoverable.addAll(other.coverable);
    final SortedSet<Integer> allCovered = new TreeSet<>(covered);
    allCovered.addAll(other.covered);
    return new FileLines(
        allCoverable,
        allCovered,
        larger(branches, other.branches),
        larger(coveredBranches, other.coveredBranches));
  }

  /** Returns the number of branches in the file. */
  public int branchCount() {
    return branches.values().stream().mapToInt(Integer::intValue).sum();
  }

  /** Returns the number of branches in the file that ran. */
  public int coveredBranchCount() {
    return coveredBranches.values().stream().mapToInt(Integer::intValue).sum();
  }

  /**
   * Keeps the larger count of each line. shortcut: JaCoCo gives branches no identity, so the larger
   * count stands for the same branches; upgrade when a report names its branches.
   */
  private static SortedMap<Integer, Integer> larger(
      final SortedMap<Integer, Integer> first, final SortedMap<Integer, Integer> second) {
    final SortedMap<Integer, Integer> merged = new TreeMap<>(first);
    second.forEach((line, count) -> merged.merge(line, count, Math::max));
    return merged;
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
