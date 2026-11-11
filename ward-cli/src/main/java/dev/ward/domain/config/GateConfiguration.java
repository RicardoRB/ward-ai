package dev.ward.domain.config;

import dev.ward.analysis.domain.rule.LayerBoundary;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One check's table in {@code ward.toml}.
 *
 * @param name the check's name, which identifies it in output and selection
 * @param type the built-in tool type that decides its behavior, such as {@code coverage}
 * @param enabled whether the check runs
 * @param command the argument vector it runs
 * @param profiles the profiles it belongs to
 * @param partial how a per-edit check narrows it, or {@code null} when it always runs in full
 * @param root the project-relative directory it runs from, or {@code null} for the project root;
 *     only OpenSpec checks read it
 * @param openApi the OpenAPI options of an OpenAPI check, or {@code null} for others
 * @param minimum the minimum score from 0 to 100 of a mutation or Swift coverage check, or {@code
 *     null} when unset
 * @param platform where an {@code xcodebuild-test} check runs, or {@code null} when its command
 *     names a destination or the check is of another type
 * @param rules the analysis rule ids a {@code ward} check runs, or empty or {@code null} to run
 *     every rule; only {@code ward} checks read it
 * @param javaClasspath the project-relative classpath file that enables Java binding resolution, or
 *     {@code null} when unset; only {@code ward} checks read it
 * @param layers the architecture boundaries a {@code ward} check enforces with {@code
 *     ward:architecture/layers}; empty when unset, and only {@code ward} checks read it
 * @param cycleRoots the project-relative source roots whose slices {@code ward:architecture/cycles}
 *     checks for cycles; empty when unset, and only {@code ward} checks read it
 * @param independence the module lists of the {@code [[checks.ward.independence]]} tables, each
 *     checked by {@code ward:architecture/independence}; empty when unset
 * @param rulesFile the project-relative path of the rule configuration file, or {@code null} for
 *     {@code ward-rules.toml}; only {@code ward} checks read it
 * @param crap the report paths and limit of a {@code crap} check, or {@code null} for others
 */
public record GateConfiguration(
    String name,
    String type,
    boolean enabled,
    List<String> command,
    Set<String> profiles,
    PartialGateConfiguration partial,
    String root,
    OpenApiGateOptions openApi,
    Integer minimum,
    XcodePlatform platform,
    List<String> rules,
    String javaClasspath,
    List<LayerBoundary> layers,
    List<String> cycleRoots,
    List<List<String>> independence,
    String rulesFile,
    CrapOptions crap) {

  /** Copies the command, profiles and rule ids, keeping their order. */
  public GateConfiguration {
    command = List.copyOf(command);
    profiles = Collections.unmodifiableSet(new LinkedHashSet<>(profiles));
    rules = rules == null ? List.of() : List.copyOf(rules);
    layers = layers == null ? List.of() : List.copyOf(layers);
    cycleRoots = cycleRoots == null ? List.of() : List.copyOf(cycleRoots);
    independence = independence == null ? List.of() : copyTables(independence);
  }

  /** Returns the module lists of the independence tables, copied so the record keeps its own. */
  public List<List<String>> independence() {
    return copyTables(independence);
  }

  private static List<List<String>> copyTables(final List<List<String>> tables) {
    return tables.stream().map(List::copyOf).toList();
  }

  /** Creates a check in the {@code default} profile whose type is its name. */
  public GateConfiguration(final String name, final boolean enabled, final List<String> command) {
    this(
        name,
        name,
        enabled,
        command,
        Set.of(WardConfiguration.DEFAULT_PROFILE),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        null,
        null);
  }
}
