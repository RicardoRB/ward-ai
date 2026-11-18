package dev.ward.analysis.domain.swift;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The type of the receiver of a Swift call or member, where the source states it: a type name, a
 * shared instance of a type, a creation, and a local or parameter whose type is declared or
 * created. shortcut: the type of any other receiver is unknown; upgrade with a type sidecar.
 */
final class SwiftReceivers {
  private static final Pattern TYPE_NAME = Pattern.compile("[A-Z]\\w*");
  private static final Pattern SHARED =
      Pattern.compile("([A-Z]\\w*)\\.(?:default|shared|standard|current|main|general|processInfo)");
  private static final Pattern CREATED = Pattern.compile("([A-Z]\\w*)\\(\\)");

  private final Map<String, String> types;

  /**
   * Creates the reader of one body.
   *
   * @param types the type of each local or parameter whose source states it
   */
  SwiftReceivers(final Map<String, String> types) {
    this.types = Map.copyOf(types);
  }

  /** The type of the receiver written as {@code qualifier}, or empty when it is unknown. */
  Optional<String> of(final String qualifier) {
    final Matcher shared = SHARED.matcher(qualifier);
    final Matcher created = CREATED.matcher(qualifier);
    if (qualifier.isEmpty()) {
      return Optional.empty();
    }
    if (TYPE_NAME.matcher(qualifier).matches()) {
      return Optional.of(qualifier);
    }
    if (shared.matches()) {
      return Optional.of(shared.group(1));
    }
    if (created.matches()) {
      return Optional.of(created.group(1));
    }
    return Optional.ofNullable(types.get(qualifier));
  }
}
