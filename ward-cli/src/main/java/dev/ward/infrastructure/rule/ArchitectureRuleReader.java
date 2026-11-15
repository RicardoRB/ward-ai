package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ArchitectureConstraint;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.analysis.domain.rule.ArchitectureSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads the {@code [[rule]]} tables of {@code ward-architecture.toml} and checks their shape. */
final class ArchitectureRuleReader {
  /** The keys of a {@code [[rule]]} table that are not constraints. */
  static final Set<String> RULE_KEYS = Set.of("name", "select", "because", "allow-empty");

  private static final String RULE = "rule";
  private static final String NAME = "name";
  private static final String BECAUSE = "because";
  private static final String SELECT = "select";
  private static final String ALLOW_EMPTY = "allow-empty";
  private static final int ONE_CONSTRAINT = 1;

  private final ArchitectureProblems problems;
  private final ArchitectureSelectorReader selectors;

  ArchitectureRuleReader(final ArchitectureProblems problems) {
    this.problems = problems;
    this.selectors = new ArchitectureSelectorReader(problems);
  }

  List<ArchitectureRule> rules(final TomlParseResult toml) {
    if (!toml.contains(RULE)) {
      return List.of();
    }
    final TomlArray tables = toml.isArray(RULE) ? toml.getArray(RULE) : null;
    if (tables == null || !allTables(tables)) {
      problems.add("", RULE, "expected [[rule]] tables");
      return List.of();
    }
    final Set<String> names = new HashSet<>();
    final List<ArchitectureRule> rules = new ArrayList<>();
    for (int index = 0; index < tables.size(); index++) {
      rule(tables.getTable(index), index, names).ifPresent(rules::add);
    }
    return rules;
  }

  private static boolean allTables(final TomlArray array) {
    return IntStream.range(0, array.size()).allMatch(array::isTable);
  }

  private Optional<ArchitectureRule> rule(
      final TomlTable table, final int index, final Set<String> names) {
    final int before = problems.count();
    final String named = table.isString(NAME) ? table.getString(NAME) : null;
    final String label = named == null ? "#" + (index + 1) : named;
    for (final String key : table.keySet()) {
      if (!RULE_KEYS.contains(key) && constraintKind(key) == null) {
        problems.add(label, key, "unknown key; expected " + supportedKeys());
      }
    }
    final String name = selectors.text(table, label, NAME);
    if (name != null && !names.add(name)) {
      problems.add(label, NAME, "the name is used by another rule");
    }
    final String because = selectors.text(table, label, BECAUSE);
    final boolean allowEmpty = allowEmpty(table, label);
    final ArchitectureSelector select = selectors.read(table, label, SELECT);
    final ArchitectureConstraint constraint = constraint(table, label);
    if (problems.count() > before) {
      return Optional.empty();
    }
    return Optional.of(new ArchitectureRule(name, select, constraint, because, allowEmpty));
  }

  private static List<String> supportedKeys() {
    final List<String> keys = new ArrayList<>(List.of(NAME, SELECT));
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      keys.add(kind.key());
    }
    keys.addAll(List.of(BECAUSE, ALLOW_EMPTY));
    return keys;
  }

  private static ArchitectureConstraint.Kind constraintKind(final String key) {
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      if (kind.key().equals(key)) {
        return kind;
      }
    }
    return null;
  }

  private ArchitectureConstraint constraint(final TomlTable table, final String label) {
    final List<ArchitectureConstraint.Kind> kinds =
        table.keySet().stream()
            .map(ArchitectureRuleReader::constraintKind)
            .filter(Objects::nonNull)
            .toList();
    if (kinds.size() != ONE_CONSTRAINT) {
      problems.add(
          label,
          kinds.isEmpty()
              ? "constraint"
              : kinds.stream().map(ArchitectureConstraint.Kind::key).toList().toString(),
          kinds.isEmpty()
              ? "expected one of " + keys()
              : "a rule has exactly one constraint, got " + kinds.size());
      return null;
    }
    final ArchitectureConstraint.Kind kind = kinds.getFirst();
    if (kind == ArchitectureConstraint.Kind.FILE_NAME) {
      final String glob = selectors.text(table, label, kind.key());
      return glob == null || !selectors.fileName(label, kind.key(), glob)
          ? null
          : new ArchitectureConstraint(kind, null, glob);
    }
    final ArchitectureSelector target = selectors.read(table, label, kind.key());
    return target == null ? null : new ArchitectureConstraint(kind, target, null);
  }

  private static List<String> keys() {
    return Arrays.stream(ArchitectureConstraint.Kind.values())
        .map(ArchitectureConstraint.Kind::key)
        .toList();
  }

  private boolean allowEmpty(final TomlTable table, final String label) {
    if (!table.contains(ALLOW_EMPTY)) {
      return false;
    }
    final Boolean flag = table.isBoolean(ALLOW_EMPTY) ? table.getBoolean(ALLOW_EMPTY) : null;
    if (flag == null) {
      problems.add(label, ALLOW_EMPTY, "expected true or false");
      return false;
    }
    return flag;
  }
}
