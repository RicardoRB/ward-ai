package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;

/**
 * What the source of a Rust file states about the types it defines: the fields of its structs.
 */
final class RustReceiverTypes {
  private static final String TRAIT = "trait_";

  private final Map<String, Map<String, String>> fields = new HashMap<>();

  /** Reads the structs, impls, traits and {@code get} methods of a file. */
  RustReceiverTypes(final SyntaxNode root) {
    collect(root, "");
  }

  /** The type of the field {@code field} of the struct {@code type}, or unknown. */
  String fieldType(final String type, final String field) {
    return fields.getOrDefault(type, Map.of()).getOrDefault(field, RustTypeNames.UNKNOWN);
  }

  private void collect(final SyntaxNode node, final String outer) {
    String inner = outer;
    if (RustTypeNames.isImpl(node)) {
      inner = RustTypeNames.implType(node);
    } else if (TRAIT.equals(node.kind())) {
      inner = RustTypeNames.traitName(node, outer);
    } else if ("structStruct".equals(node.kind()) || "tupleStruct".equals(node.kind())) {
      struct(node);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, inner);
    }
  }

  private void struct(final SyntaxNode node) {
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    if (name == null) {
      return;
    }
    final Map<String, String> own = new HashMap<>();
    for (final SyntaxNode field : RustTrees.all(node, "structField")) {
      own.put(
          RustTrees.name(RustTrees.child(field, RustKinds.IDENTIFIER)),
          RustTypeNames.declared(field)
      );
    }
    int index = 0;
    for (final SyntaxNode field : RustTrees.all(node, "tupleField")) {
      own.put(Integer.toString(index++), RustTypeNames.declared(field));
    }
    fields.put(RustTrees.name(name), own);
  }
}
