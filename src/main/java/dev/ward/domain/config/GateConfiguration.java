package dev.ward.domain.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One check's table in {@code ward.toml}.
 *
 * @param name the check's name, which identifies it in output and selection
 * @param type the built-in tool type that decides its behavior, such as {@code checkstyle}
 * @param enabled whether the check runs
 * @param command the argument vector it runs
 * @param profiles the profiles it belongs to
 * @param partial how a per-edit check narrows it, or {@code null} when it always runs in full
 * @param root the project-relative directory it runs from, or {@code null} for the project root;
 *     only OpenSpec checks read it
 * @param openApi the OpenAPI options of an OpenAPI check, or {@code null} for others
 * @param minimum the minimum score from 0 to 100 of a mutation check, or {@code null} when unset
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
    Integer minimum) {

  /** Copies the command and profiles, keeping profile order. */
  public GateConfiguration {
    command = List.copyOf(command);
    profiles = Collections.unmodifiableSet(new LinkedHashSet<>(profiles));
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
        null);
  }
}
