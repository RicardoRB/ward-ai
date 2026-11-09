package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code static func ==(lhs:rhs:) -> Bool} declared in an Objective-C class, one marked
 * {@code @objc} or inheriting {@code NSObject}, where {@code isEqual} is the method to implement,
 * like SwiftLint's {@code nsobject_prefer_isequal}.
 */
public final class SwiftNsobjectPreferIsEqualCheck implements RuleCheck {

  private static final String FUNCTION = "function_declaration";
  private static final String CLASS = "class_declaration";
  private static final Set<String> NOT_OBJC_TYPES =
      Set.of(
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "actor_declaration",
          "extension_declaration");
  private static final String HEAD = "function_head";
  private static final String NAME = "function_name";
  private static final String SIGNATURE = "function_signature";
  private static final String INHERITANCE = "type_inheritance_clause";
  private static final String BODY_SUFFIX = "body";
  private static final Pattern SELF_EQUAL = Pattern.compile("\\(lhs:([^,]+),rhs:\\1\\)->Bool");
  private static final String OBJC = "@objc";
  private static final String NS_OBJECT = "NSObject";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftNsobjectPreferIsEqualCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, new ArrayList<>(), matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node, final List<SyntaxNode> ancestors, final List<RuleMatch> matches) {
    if (FUNCTION.equals(node.kind()) && isSelfEqual(node) && isInObjcClass(ancestors)) {
      matches.add(SwiftTrees.at(node, message));
    }
    ancestors.add(node);
    node.children().forEach(child -> collect(child, ancestors, matches));
    ancestors.remove(ancestors.size() - 1);
  }

  private static boolean isSelfEqual(final SyntaxNode function) {
    final boolean isStatic =
        SwiftTrees.child(function, HEAD).map(head -> SwiftTrees.hasToken(head, "static")).orElse(false);
    final boolean named =
        SwiftTrees.child(function, NAME).map(name -> "==".equals(SwiftTrees.compact(name))).orElse(false);
    return isStatic && named && SwiftTrees.child(function, SIGNATURE)
        .map(SwiftNsobjectPreferIsEqualCheck::hasSelfEqualSignature)
        .orElse(false);
  }

  private static boolean hasSelfEqualSignature(final SyntaxNode signature) {
    return SELF_EQUAL.matcher(SwiftTrees.compact(signature)).matches();
  }

  /**
   * The nearest enclosing declaration decides: a class is Objective-C when it is marked {@code @objc}
   * or inherits {@code NSObject}; any other type declaration is not.
   */
  private static boolean isInObjcClass(final List<SyntaxNode> ancestors) {
    for (int i = ancestors.size() - 1; i >= 0; i--) {
      final SyntaxNode ancestor = ancestors.get(i);
      if (CLASS.equals(ancestor.kind())) {
        return isObjc(ancestor);
      }
      if (NOT_OBJC_TYPES.contains(ancestor.kind())) {
        return false;
      }
    }
    return false;
  }

  private static boolean isObjc(final SyntaxNode classNode) {
    final boolean marked =
        classNode.children().stream()
            .filter(child -> !child.kind().endsWith(BODY_SUFFIX))
            .anyMatch(child -> SwiftTrees.compact(child).contains(OBJC));
    final boolean inherits =
        SwiftTrees.child(classNode, INHERITANCE)
            .map(clause -> SwiftTrees.hasToken(clause, NS_OBJECT))
            .orElse(false);
    return marked || inherits;
  }
}
