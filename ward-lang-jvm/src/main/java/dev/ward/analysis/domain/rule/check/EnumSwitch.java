package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads a switch statement over an enum. The constants come from the enum declared in the same unit
 * as the switch, so an enum declared elsewhere is not read.
 */
final class EnumSwitch {

  private static final String SWITCH = "'switch'";
  private static final String DEFAULT = "'default'";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String ENUM_DECLARATION = "enumDeclaration";
  private static final String ENUM_CONSTANT = "enumConstant";

  private final SyntaxNode switchNode;
  private final List<SyntaxNode> labels;
  private final Set<String> constants;

  private EnumSwitch(
      final SyntaxNode switchNode, final List<SyntaxNode> labels, final Set<String> constants) {
    this.switchNode = switchNode;
    this.labels = labels;
    this.constants = constants;
  }

  /** Reads {@code switchNode} when it is a switch over an enum whose declaration is in the unit. */
  static Optional<EnumSwitch> of(
      final SyntaxNode switchNode,
      final Map<String, Set<String>> declared,
      final UnitTypeFacts types) {
    if (!switchNode.children().stream().anyMatch(child -> SWITCH.equals(child.kind()))) {
      return Optional.empty();
    }
    return selector(switchNode)
        .map(SyntaxNode::span)
        .flatMap(types::typeOf)
        .map(EnumSwitch::simpleName)
        .map(declared::get)
        .map(constants -> new EnumSwitch(switchNode, labels(switchNode), constants));
  }

  /** Returns the constants of every enum declared in the unit, by simple name. */
  static Map<String, Set<String>> declaredConstants(final SyntaxNode root) {
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> ENUM_DECLARATION.equals(node.kind()))
        .collect(
            Collectors.toUnmodifiableMap(
                EnumSwitch::enumName, EnumSwitch::constantsOf, (a, b) -> a));
  }

  /** Returns the switch node. */
  SyntaxNode node() {
    return switchNode;
  }

  /** Returns the {@code default} label, if present. */
  Optional<SyntaxNode> defaultLabel() {
    return labels.stream()
        .filter(label -> label.children().stream().anyMatch(c -> DEFAULT.equals(c.kind())))
        .findFirst();
  }

  /** Returns whether every constant of the enum has a case label. */
  boolean isExhaustive() {
    final Set<String> covered = new java.util.HashSet<>();
    for (final SyntaxNode label : labels) {
      label.children().stream()
          .filter(c -> EXPRESSION.equals(c.kind()))
          .forEach(c -> covered.add(SyntaxWalk.text(c)));
    }
    return covered.containsAll(constants);
  }

  private static Optional<SyntaxNode> selector(final SyntaxNode switchNode) {
    return switchNode.children().stream()
        .filter(child -> EXPRESSION.equals(child.kind()))
        .findFirst();
  }

  private static List<SyntaxNode> labels(final SyntaxNode switchNode) {
    final List<SyntaxNode> labels = new ArrayList<>();
    for (final SyntaxNode child : switchNode.children()) {
      if (GROUP.equals(child.kind())) {
        child.children().stream().filter(c -> LABEL.equals(c.kind())).forEach(labels::add);
      } else if (LABEL.equals(child.kind())) {
        labels.add(child);
      }
    }
    return labels;
  }

  private static String simpleName(final TypeRef type) {
    return type.name().substring(type.name().lastIndexOf('.') + 1);
  }

  private static String enumName(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(c -> IDENTIFIER.equals(c.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static Set<String> constantsOf(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream()
        .filter(node -> ENUM_CONSTANT.equals(node.kind()))
        .map(
            node ->
                SyntaxWalk.text(
                    node.children().stream()
                        .filter(c -> IDENTIFIER.equals(c.kind()))
                        .findFirst()
                        .orElseThrow()))
        .collect(Collectors.toUnmodifiableSet());
  }
}
