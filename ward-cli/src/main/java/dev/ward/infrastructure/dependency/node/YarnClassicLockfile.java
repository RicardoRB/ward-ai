package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the Yarn 1 {@code yarn.lock}: stanzas headed by comma-separated {@code name@range}
 * descriptors, each with an indented {@code version}, {@code resolved} and dependency sections.
 */
final class YarnClassicLockfile {
  /** Header that tells the classic format from Berry's. */
  static final String HEADER = "# yarn lockfile v1";

  private static final int STANZA_INDENT = 2;
  private static final int ENTRY_INDENT = 4;
  private static final Set<String> EDGE_SECTIONS = Set.of("dependencies", "optionalDependencies");

  private YarnClassicLockfile() {}

  /** A stanza under construction. */
  private static final class Stanza {
    private final List<String> descriptors;
    private String version = "";
    private String resolved = "";
    private final Map<String, String> requires = new HashMap<>();

    Stanza(final List<String> descriptors) {
      this.descriptors = descriptors;
    }
  }

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException on a line that is not part of the classic format
   */
  static NodeLock read(final String text) {
    final List<Stanza> stanzas = stanzas(text);
    final Map<String, Stanza> byDescriptor = new HashMap<>();
    stanzas.forEach(stanza -> stanza.descriptors.forEach(key -> byDescriptor.put(key, stanza)));
    final NodeLock.Collector collector = new NodeLock.Collector();
    for (final Stanza stanza : stanzas) {
      final Set<PackageId> required = new HashSet<>();
      stanza.requires.forEach(
          (name, range) ->
              Optional.ofNullable(byDescriptor.get(name + "@" + range))
                  .ifPresent(target -> required.add(id(target))));
      collector.add(id(stanza), released(stanza), required);
    }
    return new NodeLock(
        collector.packages(),
        (member, name, range) ->
            Optional.ofNullable(byDescriptor.get(name + "@" + range)).map(YarnClassicLockfile::id));
  }

  private static PackageId id(final Stanza stanza) {
    return NodeLock.npm(nameOf(stanza.descriptors.getFirst()), stanza.version);
  }

  private static boolean released(final Stanza stanza) {
    return stanza.resolved.isEmpty() || NodeLock.registryTarball(stanza.resolved);
  }

  /** Returns the package name of a {@code name@range} descriptor. */
  static String nameOf(final String descriptor) {
    return descriptor.substring(0, descriptor.indexOf('@', 1));
  }

  private static List<Stanza> stanzas(final String text) {
    final List<Stanza> stanzas = new ArrayList<>();
    Stanza current = null;
    String section = "";
    int number = 0;
    for (final String line : text.lines().toList()) {
      number++;
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      final int indent = line.length() - line.stripLeading().length();
      final String content = line.strip();
      if (indent == 0 && content.endsWith(":")) {
        current = new Stanza(descriptors(content.substring(0, content.length() - 1), number));
        stanzas.add(current);
        section = "";
      } else if (current == null) {
        throw new LockfileFormatException("line " + number + " is outside a package");
      } else if (indent == STANZA_INDENT) {
        section = entry(current, content, number);
      } else if (indent == ENTRY_INDENT && EDGE_SECTIONS.contains(section)) {
        final String[] pair = pair(content, number);
        current.requires.put(pair[0], pair[1]);
      } else if (indent != ENTRY_INDENT) {
        throw new LockfileFormatException(
            "line " + number + " has a shape the reader does not know");
      }
    }
    stanzas.forEach(
        stanza -> {
          if (stanza.version.isEmpty()) {
            throw new LockfileFormatException(
                "package " + stanza.descriptors.getFirst() + " has no version");
          }
        });
    return stanzas;
  }

  /** Reads one two-space entry and returns the section it opens, or an empty text. */
  private static String entry(final Stanza stanza, final String content, final int number) {
    if (content.endsWith(":")) {
      return unquote(content.substring(0, content.length() - 1));
    }
    final String[] pair = pair(content, number);
    switch (pair[0]) {
      case "version" -> stanza.version = pair[1];
      case "resolved" -> stanza.resolved = pair[1];
      default -> {
        // other scalar entries (integrity, uid) carry nothing the inventory needs
      }
    }
    return "";
  }

  private static List<String> descriptors(final String header, final int number) {
    final List<String> result = new ArrayList<>();
    for (final String part : header.split(",")) {
      final String descriptor = unquote(part.strip());
      if (descriptor.indexOf('@', 1) < 0) {
        throw new LockfileFormatException("line " + number + " is not a package descriptor");
      }
      result.add(descriptor);
    }
    return result;
  }

  private static String[] pair(final String content, final int number) {
    final int split = content.indexOf(' ');
    if (split < 0) {
      throw new LockfileFormatException("line " + number + " has a shape the reader does not know");
    }
    return new String[] {
      unquote(content.substring(0, split)), unquote(content.substring(split + 1).strip())
    };
  }

  private static String unquote(final String text) {
    return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
        ? text.substring(1, text.length() - 1)
        : text;
  }
}
