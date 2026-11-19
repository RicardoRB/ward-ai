package dev.ward.analysis.domain.rust;

/**
 * The kinds of syntax node that the Rust analysis reads. A rule node takes the name of its grammar
 * rule; a keyword or punctuation leaf takes its quoted text, and an identifier its token name.
 */
final class RustKinds {
  static final String BLOCK = "blockExpression";
  static final String EXPRESSION = "expression";
  static final String WITH_BLOCK = "expressionWithBlock";
  static final String STATEMENT = "statement";
  static final String STATEMENTS = "statements";
  static final String ITEM = "item";
  static final String VIS_ITEM = "visItem";
  static final String IDENTIFIER = "identifier";
  static final String PATTERN = "pattern";
  static final String PATTERN_NO_ALT = "patternNoTopAlt";
  static final String IDENTIFIER_PATTERN = "identifierPattern";
  static final String MATCH_ARM = "matchArm";
  static final String MATCH_ARM_EXPRESSION = "matchArmExpression";
  static final String PATH_EXPR_SEGMENT = "pathExprSegment";
  static final String PATH_IDENT_SEGMENT = "pathIdentSegment";
  static final String FUNCTION = "function_";
  static final String STRUCT = "struct_";
  static final String USE_DECLARATION = "useDeclaration";
  static final String EXTERN_CRATE = "externCrate";
  static final String EXTERN_BLOCK = "externBlock";
  static final String GENERIC_ARGS = "genericArgs";
  static final String TUPLE_INDEX = "tupleIndex";
  static final String COMPOUND_ASSIGN = "compoundAssignOperator";
  static final String STRING_LITERAL = "STRING_LITERAL";

  static final String LET = "'let'";
  static final String EQUALS = "'='";
  static final String DOT = "'.'";
  static final String ELSE = "'else'";
  static final String COLON = "':'";
  static final String MUT = "'mut'";
  static final String SELF = "'self'";
  static final String AWAIT = "'await'";
  static final String QUESTION = "'?'";
  static final String RETURN = "'return'";
  static final String BREAK = "'break'";
  static final String CONTINUE = "'continue'";

  private RustKinds() {}
}
