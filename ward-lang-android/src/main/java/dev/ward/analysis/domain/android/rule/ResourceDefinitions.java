package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the resources a file defines: a file under {@code res/<type>/} is one resource named after
 * the file, and a {@code values} file defines one resource per child of its {@code resources} root.
 */
final class ResourceDefinitions {

  private static final String VALUES = "values";
  private static final Pattern RES_FILE =
      Pattern.compile("(?:^|/)res/([a-z]+)(?:-[^/]+)?/([^/]+)\\.xml$");
  private static final Set<String> FILE_TYPES =
      Set.of("layout", "menu", "xml", "drawable", "color", "navigation");
  private static final Map<String, String> VALUE_TYPES =
      Map.ofEntries(
          Map.entry("string", "string"),
          Map.entry("color", "color"),
          Map.entry("dimen", "dimen"),
          Map.entry("bool", "bool"),
          Map.entry("integer", "integer"),
          Map.entry("style", "style"),
          Map.entry("plurals", "plurals"),
          Map.entry("string-array", "array"),
          Map.entry("integer-array", "array"),
          Map.entry("array", "array"),
          Map.entry("drawable", "drawable"));

  private ResourceDefinitions() {}

  /**
   * Returns the resources the file at {@code path} defines; none when it is not a resource file.
   */
  static List<Definition> of(final String path, final SyntaxNode root) {
    final Matcher file = RES_FILE.matcher(path);
    if (!file.find() || !AndroidSyntax.isElement(root.kind())) {
      return List.of();
    }
    final String directory = file.group(1);
    if (VALUES.equals(directory)) {
      return values(root);
    }
    if (FILE_TYPES.contains(directory)) {
      return List.of(new Definition(ResourceKey.of(directory, file.group(2)), root));
    }
    return List.of();
  }

  private static List<Definition> values(final SyntaxNode root) {
    final List<Definition> found = new ArrayList<>();
    for (final SyntaxNode child : root.children()) {
      final String type = VALUE_TYPES.get(AndroidNodes.name(child));
      final Optional<SyntaxNode> name =
          AndroidSyntax.isElement(child.kind())
              ? AndroidNodes.attribute(child, "", "name")
              : Optional.empty();
      if (type != null && name.isPresent()) {
        found.add(new Definition(ResourceKey.of(type, AndroidNodes.value(name.get())), child));
      }
    }
    return found;
  }

  /** A resource and the node that defines it. */
  record Definition(ResourceKey key, SyntaxNode node) {}
}
