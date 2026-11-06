package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.frontend.antlr.java.JavaParser.AnnotationContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.ArgumentsContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.ArrayInitializerContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.ElementValueArrayInitializerContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.EnumConstantsContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.FormalParametersContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.RecordHeaderContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.SwitchBlockStatementGroupContext;
import dev.ward.analysis.frontend.antlr.java.JavaParser.SwitchLabeledRuleContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Walks a Java parse tree once and collects the {@link JavaTreeFacts}. */
final class JavaTreeWalk {

  private static final Set<String> ANGLE_RULES =
      Set.of(
          "typeParameters",
          "typeArguments",
          "nonWildcardTypeArguments",
          "typeArgumentsOrDiamond",
          "nonWildcardTypeArgumentsOrDiamond");

  private final String[] ruleNames;
  private final List<FormatRule> rules = new ArrayList<>();
  private final Set<Integer> groupOpens = new HashSet<>();
  private final Set<Integer> arrayOpens = new HashSet<>();
  private final Set<Integer> hardAfter = new HashSet<>();
  private final Set<Integer> annotationEnds = new HashSet<>();
  private final Set<Integer> angleTokens = new HashSet<>();

  private JavaTreeWalk(final String... ruleNames) {
    this.ruleNames = ruleNames.clone();
  }

  /**
   * Walks a parse tree and collects its facts.
   *
   * @param root the root of the tree
   * @param ruleNames the grammar's rule names, indexed by rule index
   * @return the facts
   */
  static JavaTreeFacts collect(final ParseTree root, final String... ruleNames) {
    final JavaTreeWalk walk = new JavaTreeWalk(ruleNames);
    walk.visit(root);
    return new JavaTreeFacts(
        walk.rules,
        walk.groupOpens,
        walk.arrayOpens,
        walk.hardAfter,
        walk.annotationEnds,
        walk.angleTokens);
  }

  private void visit(final ParseTree node) {
    if (!(node instanceof ParserRuleContext context)) {
      return;
    }
    rules.add(new FormatRule(ruleNames[context.getRuleIndex()], context.getStart().getLine()));
    classify(context);
    if (context.children != null) {
      for (final ParseTree child : context.children) {
        visit(child);
      }
    }
  }

  private void classify(final ParserRuleContext context) {
    if (isGroupList(context)) {
      groupOpens.add(context.getStart().getTokenIndex());
    } else if (isArrayInitializer(context)) {
      arrayOpens.add(context.getStart().getTokenIndex());
    } else if (context instanceof AnnotationContext && context.getStop() != null) {
      annotationEnds.add(context.getStop().getTokenIndex());
    } else {
      classifySeparators(context);
    }
  }

  private void classifySeparators(final ParserRuleContext context) {
    if (context instanceof EnumConstantsContext) {
      addTerminals(context, ",");
    } else if (context instanceof SwitchBlockStatementGroupContext
        || context instanceof SwitchLabeledRuleContext) {
      addTerminals(context, ":");
    } else if (ANGLE_RULES.contains(ruleNames[context.getRuleIndex()])) {
      addTerminals(context, "<");
      addTerminals(context, ">");
    }
  }

  private static boolean isGroupList(final ParserRuleContext context) {
    return context instanceof ArgumentsContext
        || context instanceof FormalParametersContext
        || context instanceof RecordHeaderContext;
  }

  private static boolean isArrayInitializer(final ParserRuleContext context) {
    return context instanceof ArrayInitializerContext
        || context instanceof ElementValueArrayInitializerContext;
  }

  private Set<Integer> target(final String text) {
    return "<".equals(text) || ">".equals(text) ? angleTokens : hardAfter;
  }

  private void addTerminals(final ParserRuleContext context, final String text) {
    for (final ParseTree child : context.children) {
      if (child instanceof TerminalNode terminal && text.equals(terminal.getText())) {
        target(text).add(terminal.getSymbol().getTokenIndex());
      }
    }
  }
}
