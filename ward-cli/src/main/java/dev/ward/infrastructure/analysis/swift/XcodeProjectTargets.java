package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.swift.SwiftTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the native targets of a {@code project.pbxproj} without writing it: the Swift files of each
 * target's sources phase or synchronized folder, and the targets it declares as dependencies.
 */
final class XcodeProjectTargets {

  private static final String OBJECTS = "objects";
  private static final String ISA = "isa";
  private static final String NATIVE_TARGET = "PBXNativeTarget";
  private static final String SOURCES_PHASE = "PBXSourcesBuildPhase";
  private static final String GROUP = "PBXGroup";
  private static final String SWIFT_SUFFIX = ".swift";
  private static final String GROUP_TREE = "<group>";
  private static final String SOURCE_ROOT = "SOURCE_ROOT";
  private static final String SEPARATOR = "/";
  private static final String EMPTY = "";

  private final Map<?, ?> objects;
  private final Map<String, String> parentOf = new HashMap<>();

  private XcodeProjectTargets(final Map<?, ?> objects) {
    this.objects = objects;
    for (final Map.Entry<?, ?> entry : objects.entrySet()) {
      if (GROUP.equals(textOf(dictOf(entry.getValue()), ISA))) {
        for (final Object child : listOf(dictOf(entry.getValue()).get("children"))) {
          parentOf.put(String.valueOf(child), String.valueOf(entry.getKey()));
        }
      }
    }
  }

  /**
   * Returns the native targets of the project, in file order.
   *
   * @throws IllegalArgumentException if the project file is not a well-formed property list
   */
  static List<SwiftTarget> read(final String pbxproj) {
    final Map<String, Object> root = OldStylePlist.parse(pbxproj);
    final XcodeProjectTargets project = new XcodeProjectTargets(dictOf(root.get(OBJECTS)));
    return project.targets();
  }

  private List<SwiftTarget> targets() {
    final List<SwiftTarget> targets = new ArrayList<>();
    for (final Map.Entry<?, ?> entry : objects.entrySet()) {
      final Map<?, ?> object = dictOf(entry.getValue());
      if (NATIVE_TARGET.equals(textOf(object, ISA)) && !textOf(object, "name").isEmpty()) {
        targets.add(targetOf(textOf(object, "name"), object));
      }
    }
    return targets;
  }

  private SwiftTarget targetOf(final String name, final Map<?, ?> target) {
    final List<String> files = new ArrayList<>();
    for (final Object phase : listOf(target.get("buildPhases"))) {
      final Map<?, ?> object = dictOf(objects.get(phase));
      if (SOURCES_PHASE.equals(textOf(object, ISA))) {
        addSwiftFiles(object, files);
      }
    }
    final List<String> folders = new ArrayList<>();
    for (final Object group : listOf(target.get("fileSystemSynchronizedGroups"))) {
      pathOf(String.valueOf(group)).filter(path -> !path.isEmpty()).ifPresent(folders::add);
    }
    return new SwiftTarget(name, folders, files, dependenciesOf(target));
  }

  private void addSwiftFiles(final Map<?, ?> phase, final List<String> files) {
    for (final Object buildFile : listOf(phase.get("files"))) {
      final String reference = textOf(dictOf(objects.get(buildFile)), "fileRef");
      pathOf(reference).filter(path -> path.endsWith(SWIFT_SUFFIX)).ifPresent(files::add);
    }
  }

  private List<String> dependenciesOf(final Map<?, ?> target) {
    final List<String> names = new ArrayList<>();
    for (final Object dependency : listOf(target.get("dependencies"))) {
      final String referenced = textOf(dictOf(objects.get(dependency)), "target");
      final String name = textOf(dictOf(objects.get(referenced)), "name");
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * The path of an object from the project root, joined through its enclosing groups. Returns empty
   * for a path outside the source tree, such as a built product.
   */
  private Optional<String> pathOf(final String id) {
    String current = id;
    String resolved = EMPTY;
    for (int hops = 0; hops <= objects.size() && current != null; hops++) {
      final Map<?, ?> object = dictOf(objects.get(current));
      final String tree = textOf(object, "sourceTree");
      final String own = textOf(object, "path");
      if (SOURCE_ROOT.equals(tree)) {
        return Optional.of(join(own, resolved));
      }
      if (!tree.isEmpty() && !GROUP_TREE.equals(tree)) {
        return Optional.empty();
      }
      resolved = join(own, resolved);
      current = parentOf.get(current);
    }
    return current == null ? Optional.of(resolved) : Optional.empty();
  }

  private static String join(final String first, final String second) {
    if (first.isEmpty()) {
      return second;
    }
    return second.isEmpty() ? first : first + SEPARATOR + second;
  }

  private static Map<?, ?> dictOf(final Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static List<?> listOf(final Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private static String textOf(final Map<?, ?> object, final String key) {
    return object.get(key) instanceof String text ? text : EMPTY;
  }
}
