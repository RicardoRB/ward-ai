package dev.ward.analysis.domain.node;

import java.util.Set;

/**
 * The syntax-tree kinds the structural rules read. The TypeScript and JavaScript grammars share
 * these names; each name is checked against the real frontend by the tests.
 */
public final class NodeKinds {

  /** A braced block of statements. */
  public static final String BLOCK = "block";

  /** A statement that is the body or an else branch of a statement. */
  public static final String STATEMENT = "statement";

  /** An {@code if} statement; an {@code else if} is one nested inside the else branch. */
  public static final String IF = "ifStatement";

  /** A {@code for}, {@code while} or {@code do} loop. */
  public static final String ITERATION = "iterationStatement";

  /** A {@code switch} statement. */
  public static final String SWITCH = "switchStatement";

  /** A {@code try} statement. */
  public static final String TRY = "tryStatement";

  /** A {@code with} statement. */
  public static final String WITH = "withStatement";

  /** A {@code case} clause; the {@code default} clause has its own kind. */
  public static final String CASE = "caseClause";

  /** A {@code catch} clause. */
  public static final String CATCH = "catchProduction";

  /** An expression; logical and conditional operators are direct children of it. */
  public static final String EXPRESSION = "singleExpression";

  /** The kind of the {@code else} keyword token. */
  public static final String ELSE_TOKEN = "'else'";

  /** The kind of an identifier token. */
  public static final String IDENTIFIER_TOKEN = "Identifier";

  /** Nodes that start a function. A function also needs one of {@link #HEADER}. */
  public static final Set<String> FUNCTION =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration");

  /** Children that hold a function's parameter list. */
  public static final Set<String> HEADER =
      Set.of("callSignature", "formalParameterList", "arrowFunctionParameters");

  /** Children that hold a function's body; their statements belong to the function. */
  public static final Set<String> BODY = Set.of("functionBody", "arrowFunctionBody");

  /** Nodes that hold one formal parameter. */
  public static final Set<String> PARAMETER = Set.of("parameter", "formalParameterArg");

  /** Nodes whose first identifier is a name the naming rule checks. */
  public static final Set<String> DECLARATION =
      Set.of("functionDeclaration", "variableDeclaration", "parameter", "formalParameterArg");

  /** Nodes that add one to the complexity of their function. */
  public static final Set<String> BRANCH = Set.of(IF, ITERATION, CASE, CATCH);

  /** Statements that nest their body one level deeper. */
  public static final Set<String> NESTING = Set.of(IF, ITERATION, SWITCH, TRY, WITH);

  /** Operator tokens that add one to complexity when they appear in an expression. */
  public static final Set<String> DECISION_TOKEN = Set.of("'&&'", "'||'", "'??'", "'?'");

  private NodeKinds() {}
}
