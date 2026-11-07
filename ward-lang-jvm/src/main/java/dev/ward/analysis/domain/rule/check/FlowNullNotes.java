package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the annotations that apply to a declaration, and whether its type is {@code @NullMarked}.
 */
final class FlowNullNotes {

  private static final Set<String> HOLDERS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier");
  private static final String ANNOTATION = "annotation";

  private FlowNullNotes() {}

  /** The annotations of a parameter: its own modifiers and those of its type. */
  static List<SyntaxNode> ofParameter(final SyntaxNode parameter) {
    final List<SyntaxNode> own = new ArrayList<>(annotations(parameter));
    SyntaxWalk.firstDescendant(parameter, "typeType", Set.of())
        .ifPresent(type -> own.addAll(annotations(type)));
    return own;
  }

  /** The annotations of the member entry that holds {@code visit}'s declaration. */
  static List<SyntaxNode> notes(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> chain = visit.ancestors();
    for (int index = chain.size() - 1; index >= 0; index--) {
      final String kind = chain.get(index).kind();
      if ("classBodyDeclaration".equals(kind) || "interfaceBodyDeclaration".equals(kind)) {
        return new ArrayList<>(annotations(chain.get(index)));
      }
    }
    return new ArrayList<>();
  }

  static boolean marked(final List<SyntaxNode> ancestors) {
    for (int index = 0; index < ancestors.size(); index++) {
      final SyntaxNode node = ancestors.get(index);
      final boolean type =
          "classDeclaration".equals(node.kind()) || "interfaceDeclaration".equals(node.kind());
      if (type && index > 0 && holderMarks(ancestors, index)) {
        return true;
      }
    }
    return false;
  }

  private static boolean holderMarks(final List<SyntaxNode> ancestors, final int index) {
    final SyntaxNode wrapper = ancestors.get(index - 1);
    final SyntaxNode holder =
        "memberDeclaration".equals(wrapper.kind()) && index > 1
            ? ancestors.get(index - 2)
            : wrapper;
    return annotations(holder).stream()
        .map(FlowNullQual::simpleName)
        .anyMatch("NullMarked"::equals);
  }

  /** The annotations written in {@code holder}, inside its modifier children. */
  static List<SyntaxNode> annotations(final SyntaxNode holder) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode child : holder.children()) {
      if (ANNOTATION.equals(child.kind())) {
        out.add(child);
      } else if (HOLDERS.contains(child.kind())) {
        out.addAll(annotations(child));
      }
    }
    return out;
  }
}
