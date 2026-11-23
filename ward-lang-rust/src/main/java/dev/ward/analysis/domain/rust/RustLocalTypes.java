package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The stated types of the parameters and {@code let} locals of one Rust function. */
final class RustLocalTypes {
  private static final String LET = "letStatement";
  private static final String PARAM = "functionParamPattern";

  private RustLocalTypes() {}

  /**
   * The type of each parameter and local of {@code function} whose type is stated, by name. A name
   * that is bound twice with different types is left out.
   */
  static Map<String, String> of(final SyntaxNode function) {
    final Map<String, String> found = new HashMap<>();
    final Set<String> clashing = new HashSet<>();
    bind(function, found, clashing);
    clashing.forEach(found::remove);
    return found;
  }

  private static void bind(
      final SyntaxNode node,
      final Map<String,
      String> found,
      final Set<String> clashing
  ) {
    if (LET.equals(node.kind())) {
      declare(RustTrees.child(node, RustKinds.PATTERN_NO_ALT), letType(node), found, clashing);
    } else if (PARAM.equals(node.kind())) {
      declare(
          RustTrees.child(node, RustKinds.PATTERN),
          RustTypeNames.declared(node),
          found,
          clashing
      );
    }
    for (final SyntaxNode child : node.children()) {
      bind(child, found, clashing);
    }
  }

  private static String letType(final SyntaxNode let) {
    final String declared = RustTypeNames.declared(let);
    if (!RustTypeNames.UNKNOWN.equals(declared)) {
      return declared;
    }
    final List<SyntaxNode> parts = let.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (RustTrees.isToken(parts.get(index), RustKinds.EQUALS)) {
        return RustTypeNames.ofCreation(parts.get(index + 1));
      }
    }
    return RustTypeNames.UNKNOWN;
  }

  private static void declare(
      final SyntaxNode pattern,
      final String type,
      final Map<String,
      String> found,
      final Set<String> clashing
  ) {
    final SyntaxNode name = pattern == null ? null : RustTrees.find(pattern, RustKinds.IDENTIFIER);
    if (name == null) {
      return;
    }
    final String key = RustTrees.name(name);
    final String before = found.putIfAbsent(key, type);
    if (before != null && !Objects.equals(before, type)) {
      clashing.add(key);
    }
  }
}
