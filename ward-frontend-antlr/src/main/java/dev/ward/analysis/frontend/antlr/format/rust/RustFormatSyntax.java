package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/** The Rust grammar as the formatter's safety checks see it: tokens, rule kinds and errors. */
public final class RustFormatSyntax implements FormatSyntax {
  /** Parses the source from its first rule and reports the tokens and rule kinds in pre-order. */
  @Override
  public FormatSyntaxTree parse(final String source) {
    final RustParsing parsing = RustParsing.of(source);
    final List<FormatRule> rules = new ArrayList<>();
    collectRules(parsing, rules);
    return new FormatSyntaxTree(parsing.errorLine(), parsing.formatTokens(), rules);
  }

  private static void collectRules(final RustParsing parsing, final List<FormatRule> rules) {
    final String[] names = parsing.ruleNames();
    final Deque<ParserRuleContext> pending = new ArrayDeque<>();
    pending.push(parsing.root());
    while (!pending.isEmpty()) {
      final ParserRuleContext context = pending.pop();
      rules.add(new FormatRule(names[context.getRuleIndex()], context.getStart().getLine()));
      for (int i = context.getChildCount() - 1; i >= 0; i--) {
        if (context.getChild(i) instanceof ParserRuleContext nested) {
          pending.push(nested);
        }
      }
    }
  }
}
