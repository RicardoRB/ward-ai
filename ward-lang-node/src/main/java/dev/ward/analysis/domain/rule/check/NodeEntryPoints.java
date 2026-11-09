package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The entry points of a project: the files its manifests name, and by convention the index, test
 * and config files. Unused-file and unused-export reports skip them.
 */
final class NodeEntryPoints {

  private static final List<String> ENTRY_FIELDS =
      List.of("main", "module", "types", "typings", "bin", "exports");
  private static final Pattern SOURCE_FILE = Pattern.compile("\\.(ts|tsx|js|jsx|mjs|cjs|mts|cts)$");
  private static final Pattern CONVENTIONAL_ENTRY =
      Pattern.compile(
          "(^|/)index\\.(ts|tsx|js|jsx|mjs|cjs|mts|cts)$"
              + "|\\.(test|spec|config)\\.(ts|tsx|js|jsx|mjs|cjs|mts|cts)$");

  private NodeEntryPoints() {}

  /**
   * The project files that are entry points: the manifest fields that name a file (main, module,
   * types, bin, exports) and the script arguments that name a source file, resolved from the
   * manifest's directory, and by convention an index file, a test or a config file.
   */
  static Set<String> entryPaths(final ProjectFiles files, final Set<String> paths) {
    final Set<String> entries = new HashSet<>();
    paths.stream().filter(path -> CONVENTIONAL_ENTRY.matcher(path).find()).forEach(entries::add);
    for (final NodeManifests.Manifest owner : NodeManifests.byDirectory(files).values()) {
      entries.addAll(scriptEntries(owner, paths));
      entries.addAll(fieldEntries(owner, paths));
    }
    return entries;
  }

  /** The source files that a manifest's scripts name. */
  private static Set<String> scriptEntries(
      final NodeManifests.Manifest owner, final Set<String> paths) {
    final Set<String> entries = new HashSet<>();
    for (final String script : NodeManifests.texts(owner.manifest(), "scripts")) {
      for (final String token : script.trim().split("\\s+")) {
        final String path = resolveFrom(owner.directory(), token);
        if (SOURCE_FILE.matcher(path).find() && paths.contains(path)) {
          entries.add(path);
        }
      }
    }
    return entries;
  }

  /** The files that a manifest's entry fields name. */
  private static Set<String> fieldEntries(
      final NodeManifests.Manifest owner, final Set<String> paths) {
    final Set<String> entries = new HashSet<>();
    for (final String field : ENTRY_FIELDS) {
      owner
          .manifest()
          .member(field)
          .map(Member::value)
          .ifPresent(value -> addNamed(owner, value, paths, entries));
    }
    return entries;
  }

  private static void addNamed(
      final NodeManifests.Manifest owner,
      final Value value,
      final Set<String> paths,
      final Set<String> entries) {
    for (final String text : textLeaves(value)) {
      final String path = resolveFrom(owner.directory(), text);
      if (paths.contains(path)) {
        entries.add(path);
      }
    }
  }

  /**
   * The text values of a JSON value, at any depth: a string, or the strings inside an object or
   * array.
   */
  static List<String> textLeaves(final Value value) {
    final List<String> texts = new ArrayList<>();
    if (value instanceof Value.Text text) {
      texts.add(text.value());
    } else if (value instanceof Value.Array array) {
      array.items().forEach(item -> texts.addAll(textLeaves(item)));
    } else if (value instanceof Value.Obj object) {
      object.members().forEach(member -> texts.addAll(textLeaves(member.value())));
    }
    return texts;
  }

  /** The project-relative path of {@code value} written relative to the manifest's directory. */
  static String resolveFrom(final String directory, final String value) {
    final String relative = value.startsWith("./") ? value.substring(2) : value;
    return directory.isEmpty() ? relative : directory + "/" + relative;
  }
}
