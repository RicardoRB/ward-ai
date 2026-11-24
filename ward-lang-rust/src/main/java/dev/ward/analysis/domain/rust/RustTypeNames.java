package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names the type that a Rust type or a creating expression states: its last path segment without
 * generic arguments. A slice, array, {@code Vec} or {@code VecDeque} is {@link #SEQUENCE}, and a
 * type the source does not state is {@link #UNKNOWN}.
 */
final class RustTypeNames {
  /** The type of a slice, array, {@code Vec} or {@code VecDeque}. */
  static final String SEQUENCE = "<sequence>";

  /** The type of a value whose type the source does not state. */
  static final String UNKNOWN = "";

  private static final Set<String> SEQUENCES = Set.of("Vec", "VecDeque");
  private static final Pattern LEADING = Pattern.compile("^&(?:mut)?");
  private static final Pattern NAME = Pattern.compile("^(?:\\w+::)*(\\w+)");
  private static final Pattern CREATION = Pattern.compile("^([A-Z]\\w*)::\\w+\\(");
  private static final String TYPE = "type_";

  private RustTypeNames() {}

  /** Classifies a written type, looking through references. */
  static String ofType(final SyntaxNode type) {
    return classify(RustTrees.text(type));
  }

  /** Classifies the written type {@code text}, looking through references. */
  static String classify(final String text) {
    final String bare = RustTypeText.bare(text);
    if (bare.startsWith("[")) {
      return SEQUENCE;
    }
    return named(NAME.matcher(bare));
  }

  /** The written type of the expression that creates a value, when it states what it creates. */
  static String ofCreation(final SyntaxNode expression) {
    final String text = LEADING.matcher(RustTrees.text(expression)).replaceFirst("");
    if (text.startsWith("vec!") || text.startsWith("[")) {
      return "Vec";
    }
    final Matcher type = CREATION.matcher(text);
    return type.find() ? type.group(1) : UNKNOWN;
  }

  /** The written type that the {@code type_} child of {@code node} states, or unknown. */
  static String declaredText(final SyntaxNode node) {
    final SyntaxNode type = RustTrees.child(node, TYPE);
    return type == null ? UNKNOWN : RustTrees.text(type);
  }

  /** The type that the node {@code type_} child of {@code node} states, or {@link #UNKNOWN}. */
  static String declared(final SyntaxNode node) {
    final SyntaxNode type = RustTrees.child(node, TYPE);
    return type == null ? UNKNOWN : ofType(type);
  }

  /** The first path segment of the type of an {@code impl}, or empty. */
  static String implType(final SyntaxNode node) {
    final SyntaxNode type = RustTrees.children(node, TYPE).stream().findFirst().orElse(null);
    final SyntaxNode segment = type == null ? null : RustTrees.find(type, "typePathSegment");
    final SyntaxNode name =
        segment == null ? null : RustTrees.find(segment, RustKinds.IDENTIFIER);
    return name == null ? "" : RustTrees.name(name);
  }

  /** The name a trait declares, or {@code outer} when it has none. */
  static String traitName(final SyntaxNode trait, final String outer) {
    final SyntaxNode name = RustTrees.child(trait, RustKinds.IDENTIFIER);
    return name == null ? outer : RustTrees.name(name);
  }

  /** Whether {@code node} is an {@code impl} block. */
  static boolean isImpl(final SyntaxNode node) {
    return "inherentImpl".equals(node.kind()) || "traitImpl".equals(node.kind());
  }

  private static String named(final Matcher name) {
    if (!name.find()) {
      return UNKNOWN;
    }
    final String type = name.group(1);
    return SEQUENCES.contains(type) ? SEQUENCE : type;
  }
}
