package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;

/** A class annotated {@code @AutoValue.Builder} with a constructor that runs statements. */
public final class AutoValueBuilderConstructor implements DeclarationFactTest {

  private static final String BUILDER = "com.google.auto.value.AutoValue.Builder";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String BLOCK = "block";

  @Override
  public boolean reports(final SyntaxNode declaration, final SpanFacts facts) {
    return facts.members() != null
        && facts.members().annotations().contains(BUILDER)
        && SyntaxWalk.descendants(declaration).stream()
            .filter(node -> CONSTRUCTOR.equals(node.kind()))
            .anyMatch(AutoValueBuilderConstructor::runsStatements);
  }

  private static boolean runsStatements(final SyntaxNode constructor) {
    return constructor.children().stream()
        .filter(child -> BLOCK.equals(child.kind()))
        .map(SyntaxNode::children)
        .anyMatch(AutoValueBuilderConstructor::hasStatement);
  }

  private static boolean hasStatement(final List<SyntaxNode> parts) {
    return parts.stream().anyMatch(part -> "blockStatement".equals(part.kind()));
  }
}
