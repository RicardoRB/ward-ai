package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The method resolution order of each class of a module, by the C3 linearization, memoized. A base
 * the module does not declare is unknown, and so is the order of every class that derives from it.
 */
final class PythonClassOrder {

  private final Map<String, PythonClassModel.ClassInfo> classes;
  private final Map<String, PythonClassModel.Order> orders = new HashMap<>();

  PythonClassOrder(final Map<String, PythonClassModel.ClassInfo> classes) {
    this.classes = classes;
  }

  /** The method resolution order of a class, memoized. */
  PythonClassModel.Order order(final String name) {
    return orders.computeIfAbsent(name, key -> compute(key, new HashSet<>()));
  }

  private PythonClassModel.Order compute(final String name, final Set<String> visiting) {
    if (PythonClassModel.OBJECT.equals(name)) {
      return PythonClassModel.Order.known(List.of(PythonClassModel.OBJECT));
    }
    if (PythonClassModel.ABC.equals(name) && !classes.containsKey(name)) {
      return PythonClassModel.Order.known(List.of(PythonClassModel.ABC, PythonClassModel.OBJECT));
    }
    final PythonClassModel.ClassInfo info = classes.get(name);
    if (info == null || !visiting.add(name)) {
      return PythonClassModel.Order.unknown();
    }
    return orderOf(info, visiting);
  }

  private PythonClassModel.Order orderOf(
      final PythonClassModel.ClassInfo info, final Set<String> visiting) {
    final Optional<List<String>> bases = resolvedBases(info);
    if (bases.isEmpty() || new HashSet<>(bases.get()).size() != bases.get().size()) {
      return PythonClassModel.Order.unknown();
    }
    final List<PythonClassModel.Order> parents =
        bases.get().stream().map(base -> compute(base, new HashSet<>(visiting))).toList();
    final Optional<PythonClassModel.Order> failed =
        parents.stream()
            .filter(parent -> parent.status() != PythonClassModel.Status.KNOWN)
            .findFirst();
    if (failed.isPresent()) {
      return failed.get();
    }
    final List<List<String>> sequences = new ArrayList<>();
    parents.forEach(parent -> sequences.add(parent.names()));
    sequences.add(bases.get());
    return merge(sequences)
        .map(
            merged ->
                PythonClassModel.Order.known(
                    Stream.concat(Stream.of(info.name()), merged.stream()).toList()))
        .orElseGet(PythonClassModel.Order::inconsistent);
  }

  /** The base names as this module knows them, or empty when one of them is not known. */
  private Optional<List<String>> resolvedBases(final PythonClassModel.ClassInfo info) {
    final List<String> resolved =
        info.rawBases().stream().map(this::resolve).flatMap(Optional::stream).toList();
    return resolved.size() == info.rawBases().size() ? Optional.of(resolved) : Optional.empty();
  }

  private Optional<String> resolve(final String raw) {
    if (classes.containsKey(raw)) {
      return Optional.of(raw);
    }
    if (PythonClassModel.OBJECT.equals(raw)) {
      return Optional.of(PythonClassModel.OBJECT);
    }
    final boolean abc =
        PythonClassModel.ABC.equals(raw) || PythonClassModel.ABC_QUALIFIED.equals(raw);
    return abc ? Optional.of(PythonClassModel.ABC) : Optional.empty();
  }

  /** The C3 merge of the sequences, or empty when no order is consistent with them. */
  private static Optional<List<String>> merge(final List<List<String>> sequences) {
    final List<List<String>> work = new ArrayList<>();
    sequences.forEach(sequence -> work.add(new ArrayList<>(sequence)));
    work.removeIf(List::isEmpty);
    final List<String> merged = new ArrayList<>();
    while (!work.isEmpty()) {
      final Optional<String> next = nextHead(work);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      merged.add(next.get());
      work.forEach(sequence -> removeHead(sequence, next.get()));
      work.removeIf(List::isEmpty);
    }
    return Optional.of(merged);
  }

  private static Optional<String> nextHead(final List<List<String>> work) {
    for (final List<String> sequence : work) {
      final String head = sequence.get(0);
      if (work.stream().noneMatch(other -> other.subList(1, other.size()).contains(head))) {
        return Optional.of(head);
      }
    }
    return Optional.empty();
  }

  private static void removeHead(final List<String> sequence, final String name) {
    if (!sequence.isEmpty() && sequence.get(0).equals(name)) {
      sequence.remove(0);
    }
  }
}
