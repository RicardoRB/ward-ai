package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/** Builds the trees and runs the checks the PMD error-prone class check tests share. */
class ErrorProneTrees extends ClassMemberTrees {

  static final String MESSAGE = "m";
  static final String START = "start";
  static final int THREE = 3;

  List<RuleMatch> run(final UnitCheck check, final SyntaxNode... top) {
    return check.check(compilationUnit(top), SourceFile.UNKNOWN);
  }

  SyntaxNode constructorOf(final SyntaxNode... statements) {
    return node(
        "constructorDeclaration",
        LINE,
        COLUMN,
        ident(CLASS_NAME),
        node(BLOCK, LINE, COLUMN, statements));
  }

  SyntaxNode annotatedMember(final String annotation, final SyntaxNode method) {
    return node(
        "classBodyDeclaration",
        LINE,
        COLUMN,
        node("modifier", LINE, COLUMN, leaf("ANNOTATION", annotation, LINE, COLUMN)),
        node(MEMBER_DECLARATION, LINE, COLUMN, method));
  }
}
