package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which functions and stored properties a Swift file declares, given the context of each
 * node: a member of a type that nothing fixes is declared, and a local or a member a protocol,
 * superclass, extension or actor may fix is not.
 */
final class SwiftMemberDeclarations {

  private static final String FUNCTION = "function_declaration";
  private static final String FUNCTION_NAME = "function_name";
  private static final String VARIABLE_NAME = "variable_name";
  private static final String PATTERN = "pattern";
  private static final String PATTERN_INITIALIZER_LIST = "pattern_initializer_list";
  private static final String PATTERN_INITIALIZER = "pattern_initializer";
  private static final String INHERITANCE = "type_inheritance_clause";
  private static final String IDENTIFIER = "Identifier";
  private static final String OPEN_BRACE = "{";
  private static final int SINGLE_INITIALIZER = 1;

  /**
   * A header that only plain modifiers precede, so no protocol, superclass or attribute fixes it.
   */
  private static final Pattern PLAIN_FUNCTION_HEADER =
      Pattern.compile(
          "\\s*((private|fileprivate|internal|static|final|mutating|nonmutating)\\s+)*func\\s*");

  /** A stored property header with plain modifiers and {@code var}, which no attribute fixes. */
  private static final Pattern PLAIN_VARIABLE_HEADER =
      Pattern.compile("\\s*((private|fileprivate|internal|static|final)\\s+)*var\\s*");

  private static final Set<String> TYPE_DECLARATIONS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "typealias_declaration");

  /** The type declarations, whose bodies open a scope of members. */
  private static final Set<String> TYPE_KINDS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "extension_declaration",
          "actor_declaration");

  /** The nodes whose bodies hold local declarations, which no member rule may declare. */
  private static final Set<String> BLOCK_KINDS =
      Set.of(
          FUNCTION,
          "code_block",
          "closure_expression",
          "initializer_declaration",
          "deinitializer_declaration",
          "subscript_declaration");

  /** Types whose members a conformance or an extension may fix, whatever their inheritance. */
  private static final Set<String> OPEN_BODIES =
      Set.of("protocol_declaration", "extension_declaration", "actor_declaration");

  private SwiftMemberDeclarations() {}

  /** What the declarations under a node may be declared as. */
  enum Context {
    /** The file itself: functions may be declared, properties are not. */
    FILE,
    /** The body of a type that nothing fixes: functions and stored properties may be declared. */
    MEMBER,
    /** Inside a function or a closure: a local, so nothing is declared. */
    LOCAL,
    /** Inside a type a protocol, superclass or extension may fix: nothing is declared. */
    BLOCKED
  }

  /** The symbols and names one walk of a unit collects. */
  static final class Collected {
    final List<Reference> imports = new ArrayList<>();
    final List<Symbol> types = new ArrayList<>();
    final List<Symbol> functions = new ArrayList<>();
    final List<Symbol> properties = new ArrayList<>();

    /** The name spans of the protocols that no attribute or public access exposes. */
    final Set<Span> protocolNames = new HashSet<>();

    /** The spans of declared function and variable names: a declaration is not a use. */
    final Set<Span> declaredNames = new HashSet<>();

    /** The names of functions a protocol, superclass or attribute may fix: always used. */
    final Set<String> fixedNames = new HashSet<>();
  }

  /** The context of the children of {@code node}, given the context {@code node} has. */
  static Context childContext(final SyntaxNode node, final Context context) {
    if (context == Context.BLOCKED) {
      return Context.BLOCKED;
    }
    if (TYPE_KINDS.contains(node.kind())) {
      return opensBody(node) || context == Context.LOCAL ? Context.BLOCKED : Context.MEMBER;
    }
    if (BLOCK_KINDS.contains(node.kind())) {
      return Context.LOCAL;
    }
    return context;
  }

  /**
   * Whether the members of {@code node} may be fixed by a conformance, an extension or a subclass.
   */
  static boolean opensBody(final SyntaxNode node) {
    return OPEN_BODIES.contains(node.kind())
        || TYPE_DECLARATIONS.contains(node.kind())
            && SwiftSyntax.child(node, INHERITANCE).isPresent();
  }

  /**
   * Declares a function whose context is not blocked and whose header carries only plain modifiers.
   * Any other function keeps its name as used, since a conformance or a call through a superclass
   * may reach it without spelling the name.
   */
  static void declareFunction(
      final SyntaxNode node, final Context context, final SourceUnit unit, final Collected found) {
    final Optional<SyntaxNode> name = SwiftSyntax.child(node, FUNCTION_NAME);
    if (name.isEmpty()) {
      return;
    }
    found.declaredNames.add(name.get().span());
    final String header = header(node, name.get(), unit);
    if (context != Context.BLOCKED && PLAIN_FUNCTION_HEADER.matcher(header).matches()) {
      found.functions.add(
          new Symbol(
              SwiftSyntax.textOf(name.get()),
              name.get().span(),
              SwiftFileSummarizer.FUNCTION_KIND));
    } else {
      found.fixedNames.add(SwiftSyntax.textOf(name.get()));
    }
  }

  /**
   * Declares a stored property of a closed type, with plain modifiers, no attribute and no braces,
   * so no observer, accessor or wrapper makes a read or write that the names miss.
   */
  static void declareProperty(final SyntaxNode node, final SourceUnit unit, final Collected found) {
    final Optional<SyntaxNode> name =
        SwiftSyntax.child(node, VARIABLE_NAME).or(() -> simplePatternName(node));
    if (name.isEmpty()) {
      return;
    }
    found.declaredNames.add(name.get().span());
    final int end = name.get().span().startOffset();
    final int stop = node.span().endOffset();
    final String rest =
        end <= stop && stop <= unit.text().length() ? unit.text().substring(end, stop) : "";
    if (PLAIN_VARIABLE_HEADER.matcher(header(node, name.get(), unit)).matches()
        && !rest.contains(OPEN_BRACE)) {
      found.properties.add(
          new Symbol(
              SwiftSyntax.textOf(name.get()),
              name.get().span(),
              SwiftFileSummarizer.PROPERTY_KIND));
    }
  }

  /**
   * The name of a declaration with one pattern and no type annotation, such as {@code var x = 0}:
   * the pattern is the bare identifier.
   */
  static Optional<SyntaxNode> simplePatternName(final SyntaxNode declaration) {
    final List<SyntaxNode> initializers =
        SwiftSyntax.child(declaration, PATTERN_INITIALIZER_LIST)
            .map(
                list ->
                    list.children().stream()
                        .filter(c -> PATTERN_INITIALIZER.equals(c.kind()))
                        .toList())
            .orElse(List.of());
    if (initializers.size() != SINGLE_INITIALIZER) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> pattern = SwiftSyntax.child(initializers.get(0), PATTERN);
    return pattern
        .flatMap(SwiftMemberDeclarations::firstIdentifier)
        .filter(
            identifier -> SwiftSyntax.textOf(identifier).equals(SwiftSyntax.textOf(pattern.get())));
  }

  /** The first identifier under {@code node}, in source order, or empty. */
  static Optional<SyntaxNode> firstIdentifier(final SyntaxNode node) {
    if (IDENTIFIER.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = firstIdentifier(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** The source text from the start of {@code node} up to its name, or empty when it is unsafe. */
  static String header(final SyntaxNode node, final SyntaxNode name, final SourceUnit unit) {
    final int start = node.span().startOffset();
    final int end = name.span().startOffset();
    return start <= end && end <= unit.text().length() ? unit.text().substring(start, end) : "";
  }
}
