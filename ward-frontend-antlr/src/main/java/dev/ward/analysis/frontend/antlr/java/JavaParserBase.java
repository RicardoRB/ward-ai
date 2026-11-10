package dev.ward.analysis.frontend.antlr.java;

import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;

/**
 * Parser helper methods used by the Java grammar's semantic predicates and actions. Method names
 * start with a lower-case letter, and the grammar calls them by these names. Adapted from the
 * grammars-v4 Java grammar (BSD-3-Clause).
 */
public abstract class JavaParserBase extends Parser {

  /** Tokens that can be an identifier in an annotation value, where contextual keywords appear. */
  private static final Set<Integer> IDENTIFIER_LIKE =
      Set.of(
          JavaParser.IDENTIFIER,
          JavaParser.MODULE,
          JavaParser.OPEN,
          JavaParser.REQUIRES,
          JavaParser.EXPORTS,
          JavaParser.OPENS,
          JavaParser.TO,
          JavaParser.USES,
          JavaParser.PROVIDES,
          JavaParser.WHEN,
          JavaParser.WITH,
          JavaParser.TRANSITIVE,
          JavaParser.YIELD,
          JavaParser.SEALED,
          JavaParser.PERMITS,
          JavaParser.RECORD,
          JavaParser.VAR);

  /**
   * Creates the parser over the given token stream.
   *
   * @param input the tokens to parse
   */
  public JavaParserBase(final TokenStream input) {
    super(input);
  }

  /**
   * Tells whether the record component list just parsed is valid: a varargs component is allowed
   * only as the last one.
   *
   * @return {@code false} if a varargs component is followed by another component
   */
  public boolean doLastRecordComponent() {
    final ParserRuleContext ctx = this.getContext();
    if (!(ctx instanceof JavaParser.RecordComponentListContext)) {
      return true; // or throw if this is an unexpected state
    }

    final JavaParser.RecordComponentListContext tctx = (JavaParser.RecordComponentListContext) ctx;
    final List<JavaParser.RecordComponentContext> rcs = tctx.recordComponent();
    if (rcs.isEmpty()) {
      return true;
    }

    final int count = rcs.size();
    for (int c = 0; c < count; ++c) {
      final JavaParser.RecordComponentContext rc = rcs.get(c);
      if (rc.ELLIPSIS() != null && c + 1 < count) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tells whether the next tokens cannot be {@code identifier =}, so an annotation value may start
   * here.
   *
   * @return {@code true} unless the next two tokens are an identifier-like token and {@code =}
   */
  public boolean isNotIdentifierAssign() {
    final int la = this._input.LA(1);
    final int la2 = this._input.LA(2);
    return !IDENTIFIER_LIKE.contains(la) || la2 != JavaParser.ASSIGN;
  }
}
