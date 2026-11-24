package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the flow sites of one Kotlin body: calls and member reads with the receiver type of their
 * qualifier, the constructor calls, which are calls named {@code <init>} on the created type, and
 * the annotated parameters. Inner sites come before the sites that contain them, and a nested
 * function or class is another body, so it is not entered. A lambda is lowered as blocks of its
 * own, so the view of {@link KotlinView} keeps its calls out of the statement that holds it.
 *
 * <p>Kotlin nests a member access to the right: {@code a.b().c()} is the atom {@code a} followed by
 * a member operation that holds {@code b().c()}. The chain is read with the qualifier text so far.
 *
 * <p>shortcut: a variable's type is the last declaration of its name seen in the body, as in the
 * Java sites, and a call written with an upper-case name is a constructor; upgrade with type facts
 * when a rule needs a Kotlin function whose name starts with a capital.
 */
final class KotlinFlowSites implements FlowSites {
  private static final String CONSTRUCTOR = "<init>";
  private static final String DOT = ".";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String MEMBER = "memberAccessOperator";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String ASSIGN = "=";
  private static final Set<String> OTHER_BODIES =
      Set.of(
          "functionDeclaration",
          "classDeclaration",
          "objectDeclaration",
          "objectLiteral",
          "companionObject",
          "secondaryConstructor",
          "anonymousInitializer",
          "typeAlias"
      );

  private final JavaTypeNames types;
  private final Map<String, String> declared = new HashMap<>();
  private final Map<String, String> constructed = new HashMap<>();
  private final KotlinParameterSites parameters;
  private final KotlinView view;
  private final Map<String, String> returns;
  private final KotlinReceiverCalls receiverCalls;
  private final Set<String> allowLists;

  /**
   * Creates the finder of one body.
   *
   * @param types the imports and package of the file
   * @param view which part of a node is shown
   * @param file the types that the file declares for names its bodies share
   */
  KotlinFlowSites(final JavaTypeNames types, final KotlinView view, final KotlinFileTypes file) {
    this.types = types;
    this.view = view;
    this.returns = file.returns();
    this.receiverCalls = new KotlinReceiverCalls(file.extensions());
    this.allowLists = file.allowLists();
    declared.putAll(file.declared());
    constructed.putAll(file.constructed());
    this.parameters = new KotlinParameterSites(types, declared);
  }

  /** The names of the allow-list properties of the file. */
  Set<String> allowLists() {
    return allowLists;
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found.stream().filter(site -> view.shows(site.span())).toList();
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case POSTFIX -> chain(node, "", null, null, found);
      case "functionValueParameters" -> found.addAll(parameters.of(node));
      case "variableDeclaration" -> parameters.declare(node);
      case PROPERTY -> {
        construction(node);
        node.children().forEach(child -> walk(child, found));
      }
      default -> node.children().forEach(child -> walk(child, found));
    }
  }

  /**
   * A {@code val} with no declared type whose value is a constructor call has the type that it
   * constructs, and a call on it is reported as a call on the construction, {@code Type()}, which
   * is how a call into another file names its receiver.
   */
  private void construction(final SyntaxNode property) {
    final SyntaxNode variable = KotlinFlowSyntax.child(property, "variableDeclaration");
    if (variable == null
        || KotlinFlowSyntax.child(variable, "type") != null
        || !KotlinFlowSyntax.hasToken(property, "val")) {
      return;
    }
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(property);
    for (int index = 0; index < parts.size() - 1; index++) {
      if (ASSIGN.equals(KotlinFlowSyntax.tokenText(parts.get(index)))) {
        initialized(KotlinFlowSyntax.text(KotlinScopeWalk.simple(variable)), parts.get(index + 1));
      }
    }
  }

  /** Records the type that the initializer of the {@code val} {@code name} gives it. */
  private void initialized(final String name, final SyntaxNode initializer) {
    final String type = KotlinCalls.createdType(initializer);
    if (!type.isEmpty()) {
      declared.put(name, type);
      constructed.put(name, type);
      return;
    }
    final Optional<String> factory =
        KotlinFactoryTypes.returnType(KotlinFlowSyntax.text(initializer), types);
    final String returned = returns.get(KotlinCalls.calledName(initializer));
    if (factory.isPresent()) {
      declared.put(name, factory.get());
    } else if (returned != null) {
      declared.put(name, returned);
    }
  }

  /**
   * One atom and its operations, with the text of the receiver that came before the atom. {@code
   * origin} is where the whole expression starts and {@code before} where its receiver part ends,
   * so a call of an extension function can name its receiver as the first argument.
   */
  private void chain(
      final SyntaxNode node,
      final String qualifier,
      final Span origin,
      final Span before,
      final List<FlowSite> found
  ) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    final SyntaxNode atom = parts.get(0);
    final List<SyntaxNode> operations = parts.subList(1, parts.size());
    final String name = KotlinCalls.nameOf(atom);
    final StringBuilder path = new StringBuilder(KotlinFlowSyntax.text(atom));
    final Span start = origin == null ? atom.span() : origin;
    final Span receiver = before == null ? null : KotlinFlowSyntax.between(start, before);
    if (name.isEmpty()) {
      walk(atom, found);
    } else {
      path.setLength(0);
      path.append(qualifier.isEmpty() ? name : qualifier + DOT + name);
      if (!qualifier.isEmpty() && !KotlinCalls.startsWithCall(operations)) {
        found.addAll(reads(name, qualifier, atom.span()));
      }
    }
    String callee = name;
    Span last = atom.span();
    final KotlinReceiverCalls.Chain spans = new KotlinReceiverCalls.Chain(atom.span(), receiver);
    for (final SyntaxNode operation : operations) {
      final SyntaxNode suffix = KotlinFlowSyntax.child(operation, CALL_SUFFIX);
      if (suffix != null) {
        call(callee, qualifier, spans, operation, found);
        path.append(KotlinFlowSyntax.text(operation));
        callee = "";
      } else if (KotlinFlowSyntax.child(operation, MEMBER) != null) {
        chain(KotlinFlowSyntax.child(operation, POSTFIX), path.toString(), start, last, found);
      } else {
        operation.children().forEach(child -> walk(child, found));
        path.append(KotlinFlowSyntax.text(operation));
        callee = "";
      }
      last = operation.span();
    }
  }

  /** The arguments come first, so a call inside an argument is listed before this call. */
  private void call(
      final String name,
      final String qualifier,
      final KotlinReceiverCalls.Chain chain,
      final SyntaxNode operation,
      final List<FlowSite> found
  ) {
    final Span start = chain.start();
    final SyntaxNode suffix = KotlinFlowSyntax.child(operation, CALL_SUFFIX);
    final List<SyntaxNode> arguments = KotlinCalls.argumentsOf(suffix);
    arguments.forEach(argument -> walk(argument, found));
    KotlinFlowSyntax.children(suffix, "annotatedLambda").forEach(lambda -> walk(lambda, found));
    if (name.isEmpty()) {
      return;
    }
    final List<Span> spans = arguments.stream().map(SyntaxNode::span).toList();
    final Span span = KotlinFlowSyntax.between(start, operation.span());
    if (chain.receiver() != null
        && receiverCalls.add(name, chain, spans, operation.span(), found)) {
      return;
    }
    if (KotlinCalls.isConstructor(name, qualifier)) {
      final String written = qualifier.isEmpty() ? name : qualifier + DOT + name;
      found.add(
          new FlowSite(
              FlowSiteKind.CALL,
              CONSTRUCTOR,
              written,
              types.resolve(written),
              List.of(),
              spans,
              span
          )
      );
    } else {
      found.add(
          new FlowSite(
              FlowSiteKind.CALL,
              name,
              constructed.containsKey(qualifier) ? constructed.get(qualifier) + "()" : qualifier,
              receiverType(qualifier),
              List.of(),
              spans,
              span
          )
      );
    }
  }

  /**
   * A member read, and the call of the Java getter that the property stands for, so a rule that
   * names {@code getParameterMap} finds {@code request.parameterMap}.
   */
  private List<FlowSite> reads(final String name, final String qualifier, final Span span) {
    return List.of(
        read(name, qualifier, span),
        new FlowSite(
            FlowSiteKind.CALL,
            KotlinCalls.javaGetter(name),
            qualifier,
            receiverType(qualifier),
            List.of(),
            List.of(),
            span
        )
    );
  }

  private FlowSite read(final String name, final String qualifier, final Span span) {
    return new FlowSite(
        FlowSiteKind.MEMBER_READ,
        name,
        qualifier,
        receiverType(qualifier),
        List.of(),
        List.of(),
        span
    );
  }

  /** The type of a qualifier: a declared variable's type, or an imported or known type name. */
  private Optional<String> receiverType(final String qualifier) {
    if (qualifier.isEmpty()) {
      return Optional.empty();
    }
    final String written = declared.get(qualifier);
    return written == null ? types.resolveTypeName(qualifier) : types.resolve(written);
  }
}
