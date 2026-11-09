package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code @IBInspectable} instance property that is a {@code let}, has no explicit type,
 * or has a type Interface Builder cannot show, like SwiftLint's {@code valid_ibinspectable}. The
 * binding keyword is reported. Optional forms are allowed only for reference types.
 */
public final class SwiftValidIbinspectableCheck implements RuleCheck {

  private static final List<String> DECLARATIONS =
      List.of("variable_declaration", "constant_declaration");
  private static final Set<String> BINDINGS =
      Set.of(
          "pattern_initializer_list",
          "variable_name",
          "type_annotation",
          "code_block",
          "getter_setter_block",
          "initializer",
          "pattern");
  private static final String IB_INSPECTABLE = "IBInspectable";
  private static final String LET = "let";
  private static final String VAR = "var";
  private static final String STATIC = "static";
  private static final String CLASS = "class";
  private static final String TYPE = "type_";
  private static final String TYPE_ANNOTATION = "type_annotation";
  private static final Set<String> VALUE_TYPES =
      Set.of(
          "Bool", "Int", "Int8", "Int16", "Int32", "Int64", "UInt", "UInt8", "UInt16", "UInt32",
          "UInt64", "Float", "Double", "CGFloat", "CGPoint", "CGSize", "CGRect");
  private static final Set<String> REFERENCE_TYPES =
      Set.of("String", "NSString", "UIColor", "NSColor", "UIImage", "NSImage", "UIFont", "NSFont");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftValidIbinspectableCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final String kind : DECLARATIONS) {
      for (final SyntaxNode declaration : SwiftTrees.descendants(root, kind)) {
        final List<SyntaxNode> header = header(declaration);
        if (isInspectableInstanceVariable(header) && hasViolation(declaration, header)) {
          keyword(header).ifPresent(keyword -> matches.add(SwiftTrees.at(keyword, message)));
        }
      }
    }
    return List.copyOf(matches);
  }

  /** The children before the bindings: attributes, modifiers and the binding keyword. */
  private static List<SyntaxNode> header(final SyntaxNode declaration) {
    return declaration.children().stream().filter(child -> !BINDINGS.contains(child.kind())).toList();
  }

  private static boolean isInspectableInstanceVariable(final List<SyntaxNode> header) {
    final boolean inspectable = header.stream().anyMatch(node -> SwiftTrees.compact(node).contains(IB_INSPECTABLE));
    final boolean instance =
        header.stream().noneMatch(node -> SwiftTrees.hasToken(node, STATIC) || SwiftTrees.hasToken(node, CLASS));
    return inspectable && instance;
  }

  private static boolean hasViolation(final SyntaxNode declaration, final List<SyntaxNode> header) {
    if (keyword(header).map(leaf -> LET.equals(leaf.fields().get("text"))).orElse(false)) {
      return true;
    }
    return declaredType(declaration).map(type -> !isSupported(type)).orElse(true);
  }

  /** The declared type text, without the optional markers, when there is an explicit type. */
  private static Optional<SyntaxNode> declaredType(final SyntaxNode declaration) {
    return SwiftTrees.descendants(declaration, TYPE_ANNOTATION).stream()
        .findFirst()
        .flatMap(annotation -> SwiftTrees.child(annotation, TYPE));
  }

  private static boolean isSupported(final SyntaxNode type) {
    final String text = SwiftTrees.compact(type);
    final boolean optional = text.endsWith("?") || text.endsWith("!");
    final String base = optional ? text.substring(0, text.length() - 1) : text;
    if (VALUE_TYPES.contains(base)) {
      return !optional;
    }
    return REFERENCE_TYPES.contains(base);
  }

  private static Optional<SyntaxNode> keyword(final List<SyntaxNode> header) {
    return header.stream()
        .flatMap(node -> SwiftTrees.leaves(node).stream())
        .filter(leaf -> LET.equals(leaf.fields().get("text")) || VAR.equals(leaf.fields().get("text")))
        .findFirst();
  }
}
