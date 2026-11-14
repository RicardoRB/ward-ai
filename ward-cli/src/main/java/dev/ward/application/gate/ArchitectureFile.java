package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.ArchitectureRule;
import java.util.List;
import java.util.Objects;

/**
 * What {@code ward-architecture.toml} says: the general architecture rules, or the problems that
 * stop the gate.
 *
 * @param name the project-relative path of the file, used to name it in every problem
 * @param rules the rules, in file order; empty when the file has problems
 * @param problems the problems of the file, each naming the file, the rule and the key
 */
public record ArchitectureFile(String name, List<ArchitectureRule> rules, List<String> problems) {

  /** The file name {@code ward-architecture.toml} at the project root, used by default. */
  public static final String DEFAULT_NAME = "ward-architecture.toml";

  /** Validates and copies the parts. */
  public ArchitectureFile {
    Objects.requireNonNull(name, "name");
    rules = List.copyOf(rules);
    problems = List.copyOf(problems);
  }

  /** Returns a file that only reports {@code problem}. */
  public static ArchitectureFile unusable(final String name, final String problem) {
    return new ArchitectureFile(name, List.of(), List.of(name + ": " + problem));
  }
}
