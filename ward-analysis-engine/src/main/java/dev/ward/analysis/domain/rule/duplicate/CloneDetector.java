package dev.ward.analysis.domain.rule.duplicate;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.duplicate.DuplicateTokens.Token;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Finds the token sequences that repeat across files, as PMD CPD and jscpd do. Windows of the
 * minimum length are hashed with a rolling polynomial hash and grouped by exact comparison, and
 * each group is reported once, at the place where its copies first differ before them, extended to
 * the longest run that all copies share. Copies that overlap inside one file are kept once.
 */
public final class CloneDetector {

  /** One place where a repeated sequence occurs: its file and the span of the whole copy. */
  public record Copy(String path, Span span) {}

  /** A token sequence of {@code length} tokens that occurs at two or more places. */
  public record Clone(int length, List<Copy> copies) {}

  private static final long BASE = 1_000_003L;
  private static final Comparator<Clone> ORDER =
      Comparator.comparing((Clone clone) -> clone.copies().get(0).path())
          .thenComparingInt(clone -> clone.copies().get(0).span().startLine())
          .thenComparingInt(clone -> clone.copies().get(0).span().startColumn());

  private CloneDetector() {}

  /**
   * Returns the repeated sequences of at least {@code minTokens} tokens among {@code files}, keyed
   * by path, in path and source order.
   *
   * @throws IllegalArgumentException if {@code minTokens} is below one
   */
  public static List<Clone> detect(final SortedMap<String, List<Token>> files, final int minTokens) {
    if (minTokens < 1) {
      throw new IllegalArgumentException("minTokens must be at least 1");
    }
    final Corpus corpus = new Corpus(files);
    final List<Clone> clones = new ArrayList<>();
    for (final List<Integer> bucket : corpus.windowsByHash(minTokens).values()) {
      for (final List<Integer> members : corpus.sequenceClasses(bucket, minTokens)) {
        if (members.size() >= 2) {
          corpus.clone(members, minTokens).ifPresent(clones::add);
        }
      }
    }
    clones.sort(ORDER);
    return List.copyOf(clones);
  }

  /** The tokens of every file in one array, with the file and end of each position. */
  private static final class Corpus {
    private final List<String> paths = new ArrayList<>();
    private final int[] ids;
    private final int[] fileOf;
    private final int[] fileEnd;
    private final Span[] spans;
    private final long[] prefix;

    Corpus(final SortedMap<String, List<Token>> files) {
      final int total = files.values().stream().mapToInt(List::size).sum();
      ids = new int[total];
      fileOf = new int[total];
      fileEnd = new int[total];
      spans = new Span[total];
      prefix = new long[total + 1];
      final Map<String, Integer> interned = new HashMap<>();
      int index = 0;
      for (final Map.Entry<String, List<Token>> file : files.entrySet()) {
        final int first = index;
        paths.add(file.getKey());
        for (final Token token : file.getValue()) {
          ids[index] = interned.computeIfAbsent(token.value(), value -> interned.size());
          spans[index] = token.span();
          fileOf[index] = paths.size() - 1;
          index++;
        }
        for (int position = first; position < index; position++) {
          fileEnd[position] = index;
        }
      }
      for (int position = 0; position < total; position++) {
        prefix[position + 1] = prefix[position] * BASE + ids[position] + 1;
      }
    }

    /** The starts of every window of {@code length} tokens inside one file, grouped by hash. */
    Map<Long, List<Integer>> windowsByHash(final int length) {
      final Map<Long, List<Integer>> buckets = new LinkedHashMap<>();
      final long power = power(length);
      for (int start = 0; start < ids.length; start++) {
        if (fits(start, length)) {
          final long hash = prefix[start + length] - prefix[start] * power;
          buckets.computeIfAbsent(hash, key -> new ArrayList<>()).add(start);
        }
      }
      return buckets;
    }

    /** Splits the starts of one hash bucket into groups whose windows are equal token for token. */
    List<List<Integer>> sequenceClasses(final List<Integer> bucket, final int length) {
      final List<List<Integer>> classes = new ArrayList<>();
      for (final int start : bucket) {
        final Optional<List<Integer>> same =
            classes.stream().filter(group -> sameWindow(group.get(0), start, length)).findFirst();
        if (same.isPresent()) {
          same.get().add(start);
        } else {
          classes.add(new ArrayList<>(List.of(start)));
        }
      }
      return classes;
    }

    /**
     * The clone of the sequence at {@code members}, or nothing when the copies share their token
     * before them, since that longer clone is reported at the place where the copies first differ.
     */
    Optional<Clone> clone(final List<Integer> members, final int length) {
      if (sharesPredecessor(members)) {
        return Optional.empty();
      }
      final int extended = capToNonOverlapping(members, extend(members, length));
      if (extended < length) {
        return Optional.empty();
      }
      final List<Copy> copies = new ArrayList<>();
      for (final int member : members) {
        copies.add(new Copy(paths.get(fileOf[member]), span(member, extended)));
      }
      return Optional.of(new Clone(extended, copies));
    }

    /**
     * Shortens {@code extended} to the distance between neighbouring copies in one file, so that
     * no two copies of the clone overlap; a run that overlaps itself is a shorter clone, if any.
     */
    private int capToNonOverlapping(final List<Integer> members, final int extended) {
      int capped = extended;
      for (int index = 1; index < members.size(); index++) {
        final int before = members.get(index - 1);
        final int after = members.get(index);
        if (fileOf[before] == fileOf[after]) {
          capped = Math.min(capped, after - before);
        }
      }
      return capped;
    }

    /** Whether every member has a token before it in its file, and all those tokens are equal. */
    private boolean sharesPredecessor(final List<Integer> members) {
      final int first = members.get(0);
      if (first == 0 || fileOf[first - 1] != fileOf[first]) {
        return false;
      }
      for (final int member : members) {
        if (member == 0
            || fileOf[member - 1] != fileOf[member]
            || ids[member - 1] != ids[first - 1]) {
          return false;
        }
      }
      return true;
    }

    /** The longest length, from {@code length}, that every member's window shares. */
    private int extend(final List<Integer> members, final int length) {
      int extended = length;
      while (sharesToken(members, extended)) {
        extended++;
      }
      return extended;
    }

    /** Whether every member has a token at {@code offset} and all those tokens are equal. */
    private boolean sharesToken(final List<Integer> members, final int offset) {
      for (final int member : members) {
        if (!fits(member, offset + 1)) {
          return false;
        }
      }
      final int expected = ids[members.get(0) + offset];
      for (final int member : members) {
        if (ids[member + offset] != expected) {
          return false;
        }
      }
      return true;
    }

    private boolean sameWindow(final int left, final int right, final int length) {
      for (int offset = 0; offset < length; offset++) {
        if (ids[left + offset] != ids[right + offset]) {
          return false;
        }
      }
      return true;
    }

    /** Whether a window of {@code length} tokens starting at {@code start} stays in its file. */
    private boolean fits(final int start, final int length) {
      return start + length <= fileEnd[start];
    }

    private Span span(final int start, final int length) {
      final Span first = spans[start];
      final Span last = spans[start + length - 1];
      return new Span(
          first.startLine(),
          first.startColumn(),
          last.endLine(),
          last.endColumn(),
          first.startOffset(),
          last.endOffset());
    }

    private static long power(final int length) {
      long power = 1;
      for (int step = 0; step < length; step++) {
        power *= BASE;
      }
      return power;
    }
  }
}
