package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Builds the cache keys of the {@code ward} gate's per-unit results. */
final class AnalysisCacheKey {
  private static final String ALGORITHM = "SHA-256";

  private AnalysisCacheKey() {}

  /**
   * Hashes the engine version and the rules selected for one language. The rules are hashed in id
   * order, so the order they were listed in does not change the hash.
   */
  static String ruleSet(final String engineVersion, final List<RuleDefinition> rules) {
    final List<String> parts = new ArrayList<>();
    parts.add(engineVersion);
    rules.stream()
        .sorted(Comparator.comparing(RuleDefinition::id))
        .forEach(
            rule -> {
              parts.add(rule.id());
              parts.add(rule.languages().stream().map(Enum::name).sorted().toList().toString());
              parts.add(rule.capability().name());
              parts.add(rule.severity().name());
              parts.add(rule.body());
            });
    return digest(parts);
  }

  /** Hashes the rule set and the unit's path and full text. */
  static String unit(final String ruleSetHash, final SourceUnit unit) {
    return digest(List.of(ruleSetHash, unit.path(), unit.text()));
  }

  /**
   * Hashes the rule set, the unit's path and full text, and the files around it, with the path and
   * full text of each file in {@code related}. Used when the rule set has a rule that reads
   * dependencies; {@code related} is empty unless it has a taint rule, since a value can cross a
   * call into any file the unit depends on, so a change of one changes the findings. shortcut: the
   * whole text is hashed, so a comment edit in a callee re-analyzes its dependents; upgrade by
   * hashing a taint summary per file and rule specification.
   */
  static String unit(
      final String ruleSetHash,
      final SourceUnit unit,
      final DependencyView view,
      final List<SourceUnit> related) {
    final List<String> parts = new ArrayList<>();
    parts.add(ruleSetHash);
    parts.add(unit.path());
    parts.add(unit.text());
    parts.add(dependencies(view));
    related.forEach(
        other -> {
          parts.add(other.path());
          parts.add(other.text());
        });
    return digest(parts);
  }

  /** Hashes the rule set and the path and full text of a text file. */
  static String text(final String ruleSetHash, final TextFile file) {
    return digest(List.of("text", ruleSetHash, file.path(), file.text()));
  }

  /** Hashes the summary's key: the engine version, the path and the full text of the unit. */
  static String summary(final String engineVersion, final SourceUnit unit) {
    return digest(List.of("summary", engineVersion, unit.path(), unit.text()));
  }

  /** The fixed key of the reverse-dependency index. */
  static String index() {
    return digest(List.of("reverse-dependency-index"));
  }

  /**
   * Hashes, in path order, each direct dependency and dependent with its path and the hash of its
   * names. A file's names change only when its exports, imports or declarations change, so a body
   * edit that keeps them does not invalidate the files around it.
   */
  private static String dependencies(final DependencyView view) {
    final List<String> parts = new ArrayList<>();
    view.dependencies().forEach(summary -> add(parts, "dependency", summary));
    view.dependents().forEach(summary -> add(parts, "dependent", summary));
    return digest(parts);
  }

  private static void add(final List<String> parts, final String role, final FileSummary summary) {
    parts.add(role);
    parts.add(summary.path());
    parts.add(names(summary));
  }

  /**
   * Hashes the names of the summary, not their positions, so an edit that moves a declaration keeps
   * the names. shortcut: a rule that reads positions of other files would also need them in this
   * hash, and then a line shift would re-analyze the files around it.
   */
  private static String names(final FileSummary summary) {
    final List<String> parts = new ArrayList<>();
    parts.add("exports");
    summary.exports().stream().map(Symbol::name).sorted().forEach(parts::add);
    parts.add("imports");
    summary.imports().stream().map(Reference::specifier).sorted().forEach(parts::add);
    parts.add("declared");
    summary.declared().stream().map(Symbol::name).sorted().forEach(parts::add);
    return digest(parts);
  }

  /** Hashes each part behind its length, so no two different part lists share a digest. */
  private static String digest(final List<String> parts) {
    try {
      final MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      for (final String part : parts) {
        final byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException(ALGORITHM + " is not available", exception);
    }
  }
}
