package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Types the expressions of one Python file from literals, annotations, initializers, source
 * definitions and stubs. Anything it cannot determine is unknown, never a guess.
 */
final class PythonExpressionTypes {

  private static final int MAX_DEPTH = 8;
  private static final int SINGLE_PART = 1;
  private static final int PAIR_PARTS = 2;
  private static final int TYPED_PARAMETER_PARTS = 3;
  private static final String AWAIT = "await";
  private static final String ATTRIBUTE = ".";
  private static final String CALL_OPEN = "(";
  private static final String TUPLE_OPEN = "(";
  private static final String LIST_OPEN = "[";
  private static final String SET_OPEN = "{";
  private static final String COMMA = ",";
  private static final String ANNOTATION_ARROW = "->";
  private static final Map<String, String> TYPING_ALIASES =
      Map.of(
          "List", "list", "Dict", "dict", "Set", "set", "FrozenSet", "frozenset", "Tuple", "tuple");
  private static final String TYPING = "typing";
  private static final String TRUE = "True";
  private static final String FALSE = "False";
  private static final String NONE = "None";
  private static final String BOOL = "bool";
  private static final String STR = "str";
  private static final String BYTES = "bytes";
  private static final String INT = "int";
  private static final String FLOAT = "float";
  private static final String COMPLEX = "complex";
  private static final String TUPLE = "tuple";
  private static final String LIST = "list";
  private static final String DICT = "dict";
  private static final String SET = "set";
  private static final String HEX = "0x";
  private static final String OCTAL = "0o";
  private static final String BINARY = "0b";
  private static final String UNPACK_MARKERS = "*";
  private static final String KEYWORD_MARKERS = "**";
  private static final String POSITIONAL_ONLY = "/";

  private final PythonTypeIndex index;
  private final PythonStubs stubs;
  private final PythonAnnotationTypes sourceAnnotations;

  /** Creates the typer over the indexed file and the stubs it may consult. */
  PythonExpressionTypes(final PythonTypeIndex index, final PythonStubs stubs) {
    this.index = index;
    this.stubs = stubs;
    this.sourceAnnotations = new PythonAnnotationTypes(this::sourceName);
  }

  /** Returns the type of {@code expression}, or empty when it is not known. */
  Optional<TypeRef> typeOf(final SyntaxNode expression) {
    return Optional.ofNullable(evaluate(expression, 0).type());
  }

  /** Returns the target of the call that {@code expression} is, when it is a call of a stub. */
  Optional<CallTarget> callOf(final SyntaxNode expression) {
    return Optional.ofNullable(evaluate(expression, 0).call());
  }

  /** What an expression denotes: a value, a module, a class or a function. */
  private enum Kind {
    VALUE,
    MODULE,
    CLASS,
    SOURCE_FUNCTION,
    STUB_FUNCTION,
    UNKNOWN
  }

  /**
   * The denotation of an expression. {@code type} is the value's type for a value and the owner for
   * a method; {@code name} is the module, class or owner name; {@code call} is set when the
   * expression is a call of a stub.
   */
  private record Subject(
      Kind kind, TypeRef type, String name, SyntaxNode definition, String module, CallTarget call) {

    static final Subject UNKNOWN = new Subject(Kind.UNKNOWN, null, null, null, null, null);

    static Subject value(final TypeRef type) {
      return new Subject(Kind.VALUE, type, null, null, null, null);
    }

    static Subject module(final String name) {
      return new Subject(Kind.MODULE, null, name, null, null, null);
    }

    static Subject klass(final String name) {
      return new Subject(Kind.CLASS, null, name, null, null, null);
    }

    static Subject sourceFunction(final SyntaxNode definition) {
      return new Subject(Kind.SOURCE_FUNCTION, null, null, definition, null, null);
    }

    static Subject stubFunction(final PythonStubs.StubFunction function, final String owner) {
      return new Subject(
          Kind.STUB_FUNCTION, null, owner, function.definition(), function.module(), null);
    }
  }

  private Subject evaluate(final SyntaxNode raw, final int depth) {
    if (depth > MAX_DEPTH) {
      return Subject.UNKNOWN;
    }
    final SyntaxNode node = PythonTypeKinds.descend(raw);
    if (PythonTypeKinds.ATOM_EXPR.equals(node.kind())) {
      return chain(node, depth);
    }
    if (PythonTypeKinds.ATOM.equals(node.kind())) {
      return atom(node, depth);
    }
    if (PythonTypeKinds.isName(node)) {
      return name(node, depth);
    }
    return literal(node);
  }

  private Subject literal(final SyntaxNode token) {
    final String text = PythonSyntax.text(token);
    if (PythonTypeKinds.NUMBER.equals(token.kind())) {
      return Subject.value(TypeRef.of(numberType(text)));
    }
    if (PythonTypeKinds.STRING.equals(token.kind())) {
      return Subject.value(TypeRef.of(stringType(text)));
    }
    if (TRUE.equals(text) || FALSE.equals(text)) {
      return Subject.value(TypeRef.of(BOOL));
    }
    return NONE.equals(text) ? Subject.value(TypeRef.of(NONE)) : Subject.UNKNOWN;
  }

  private static String numberType(final String text) {
    final String lower = text.toLowerCase(Locale.ROOT);
    if (lower.endsWith("j")) {
      return COMPLEX;
    }
    if (lower.startsWith(HEX) || lower.startsWith(OCTAL) || lower.startsWith(BINARY)) {
      return INT;
    }
    return lower.contains(".") || lower.contains("e") ? FLOAT : INT;
  }

  private static String stringType(final String text) {
    final int quote = Math.max(text.indexOf('\''), text.indexOf('"'));
    final String prefix = quote < 0 ? "" : text.substring(0, quote).toLowerCase(Locale.ROOT);
    return prefix.contains("b") ? BYTES : STR;
  }

  private Subject atom(final SyntaxNode node, final int depth) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == SINGLE_PART) {
      return evaluate(parts.get(0), depth + 1);
    }
    if (parts.stream().allMatch(part -> PythonTypeKinds.STRING.equals(part.kind()))) {
      return literal(parts.get(0));
    }
    final String open = PythonSyntax.text(parts.get(0));
    if (TUPLE_OPEN.equals(open)) {
      return parenthesized(parts, depth);
    }
    if (LIST_OPEN.equals(open)) {
      return builtinValue(LIST);
    }
    return SET_OPEN.equals(open) ? braces(parts) : Subject.UNKNOWN;
  }

  private Subject parenthesized(final List<SyntaxNode> parts, final int depth) {
    if (parts.size() == PAIR_PARTS) {
      return builtinValue(TUPLE);
    }
    final SyntaxNode inner = parts.get(1);
    if (!PythonTypeKinds.TESTLIST_COMP.equals(inner.kind())) {
      return Subject.UNKNOWN;
    }
    final List<SyntaxNode> items = inner.children();
    if (items.stream().anyMatch(item -> PythonTypeKinds.COMP_FOR.equals(item.kind()))) {
      return Subject.UNKNOWN;
    }
    if (items.stream().anyMatch(item -> COMMA.equals(PythonSyntax.text(item)))) {
      return builtinValue(TUPLE);
    }
    return items.isEmpty() ? Subject.UNKNOWN : evaluate(items.get(0), depth + 1);
  }

  private Subject braces(final List<SyntaxNode> parts) {
    if (parts.size() == PAIR_PARTS) {
      return builtinValue(DICT);
    }
    final boolean mapping =
        PythonTypeKinds.DICTORSETMAKER.equals(parts.get(1).kind())
            && parts.get(1).children().stream()
                .map(PythonSyntax::text)
                .anyMatch(text -> ":".equals(text) || KEYWORD_MARKERS.equals(text));
    return builtinValue(mapping ? DICT : SET);
  }

  private Subject chain(final SyntaxNode node, final int depth) {
    final List<SyntaxNode> parts = node.children();
    if (AWAIT.equals(PythonSyntax.text(parts.get(0)))) {
      return Subject.UNKNOWN;
    }
    Subject current = evaluate(parts.get(0), depth + 1);
    for (final SyntaxNode trailer : parts.subList(1, parts.size())) {
      current = apply(current, trailer);
    }
    return current;
  }

  private Subject apply(final Subject current, final SyntaxNode trailer) {
    final String symbol = PythonSyntax.text(trailer.children().get(0));
    if (ATTRIBUTE.equals(symbol)) {
      return attribute(current, PythonNames.identifier(trailer.children().get(1)));
    }
    return CALL_OPEN.equals(symbol) ? invoke(current) : Subject.UNKNOWN;
  }

  private Subject attribute(final Subject current, final String member) {
    if (current.kind() == Kind.MODULE) {
      return moduleMember(current.name(), member);
    }
    final TypeRef owner = current.kind() == Kind.VALUE ? current.type() : null;
    if (owner == null || owner.arrayDepth() != 0) {
      return Subject.UNKNOWN;
    }
    return stubs
        .method(owner.name(), member)
        .map(function -> Subject.stubFunction(function, owner.name()))
        .orElse(Subject.UNKNOWN);
  }

  private Subject moduleMember(final String module, final String member) {
    final String qualified = module + "." + member;
    final Optional<PythonStubs.StubFunction> function = stubs.function(module, member);
    if (function.isPresent()) {
      return Subject.stubFunction(function.get(), null);
    }
    return stubs.isClass(qualified) ? Subject.klass(qualified) : Subject.module(qualified);
  }

  private Subject invoke(final Subject current) {
    return switch (current.kind()) {
      case STUB_FUNCTION -> invokeStub(current);
      case CLASS -> instance(current.name());
      case SOURCE_FUNCTION -> sourceReturn(current.definition());
      default -> Subject.UNKNOWN;
    };
  }

  private Subject invokeStub(final Subject current) {
    final SyntaxNode definition = current.definition();
    final boolean method = current.name() != null;
    final TypeRef returns = stubReturn(definition, current.module()).orElse(null);
    final String owner = method ? current.name() : current.module();
    final CallTarget call =
        new CallTarget(
            TypeRef.of(owner),
            PythonNames.identifier(definition.children().get(1)),
            stubParameters(definition, current.module(), method),
            returns,
            false,
            false);
    return new Subject(Kind.VALUE, returns, null, null, null, call);
  }

  /** The parameter types of a stub function, or none when the signature is not plain. */
  private List<TypeRef> stubParameters(
      final SyntaxNode definition, final String module, final boolean method) {
    final PythonAnnotationTypes annotations =
        new PythonAnnotationTypes(node -> stubName(module, node));
    final Optional<List<SyntaxNode>> parsed = parameterItems(definition);
    if (parsed.isEmpty()) {
      return List.of();
    }
    final List<SyntaxNode> items = parsed.get();
    final List<TypeRef> types = new ArrayList<>();
    for (int position = method ? 1 : 0; position < items.size(); position++) {
      final SyntaxNode item = items.get(position);
      if (!PythonTypeKinds.TFPDEF.equals(item.kind())
          || item.children().size() != TYPED_PARAMETER_PARTS) {
        return List.of();
      }
      final Optional<TypeRef> type = annotations.of(item.children().get(2));
      if (type.isEmpty()) {
        return List.of();
      }
      types.add(type.get());
    }
    return types;
  }

  /** The typed parameters of {@code definition}, or empty for a signature with markers. */
  private static Optional<List<SyntaxNode>> parameterItems(final SyntaxNode definition) {
    final List<SyntaxNode> items = new ArrayList<>();
    final Optional<SyntaxNode> list =
        PythonSyntax.children(definition, PythonTypeKinds.PARAMETERS).stream()
            .flatMap(
                parameters ->
                    PythonSyntax.children(parameters, PythonTypeKinds.TYPEDARGSLIST).stream())
            .findFirst();
    if (list.isEmpty()) {
      return Optional.of(items);
    }
    for (final SyntaxNode item : list.get().children()) {
      final String text = PythonSyntax.text(item);
      if (UNPACK_MARKERS.equals(text)
          || KEYWORD_MARKERS.equals(text)
          || POSITIONAL_ONLY.equals(text)) {
        return Optional.empty();
      }
      if (PythonTypeKinds.TFPDEF.equals(item.kind())) {
        items.add(item);
      }
    }
    return Optional.of(items);
  }

  private Subject sourceReturn(final SyntaxNode definition) {
    return returnAnnotation(definition)
        .flatMap(sourceAnnotations::of)
        .map(Subject::value)
        .orElse(Subject.UNKNOWN);
  }

  private Optional<TypeRef> stubReturn(final SyntaxNode definition, final String module) {
    return returnAnnotation(definition)
        .flatMap(
            annotation -> new PythonAnnotationTypes(node -> stubName(module, node)).of(annotation));
  }

  private static Optional<SyntaxNode> returnAnnotation(final SyntaxNode definition) {
    final List<SyntaxNode> parts = definition.children();
    final int arrow = PythonNames.indexOf(parts, ANNOTATION_ARROW);
    return arrow < 0 ? Optional.empty() : Optional.of(parts.get(arrow + 1));
  }

  private Subject name(final SyntaxNode node, final int depth) {
    final Optional<Binding> binding = index.bindingOf(node);
    if (binding.isPresent()) {
      return bound(binding.get(), depth);
    }
    return builtinName(PythonNames.identifier(node));
  }

  private Subject bound(final Binding binding, final int depth) {
    return switch (binding.kind()) {
      case PARAMETER, VARIABLE -> Subject.value(declaredType(binding, depth));
      case FUNCTION -> index.function(binding).map(Subject::sourceFunction).orElse(Subject.UNKNOWN);
      case CLASS -> Subject.klass(binding.name());
      case IMPORT -> imported(binding.origin());
      default -> Subject.UNKNOWN;
    };
  }

  private TypeRef declaredType(final Binding binding, final int depth) {
    final Optional<TypeRef> annotated = index.annotation(binding).flatMap(sourceAnnotations::of);
    if (annotated.isPresent()) {
      return annotated.get();
    }
    return index
        .initializer(binding)
        .flatMap(initializer -> Optional.ofNullable(evaluate(initializer, depth + 1).type()))
        .orElse(null);
  }

  private Subject imported(final Optional<ImportOrigin> origin) {
    if (origin.isEmpty()) {
      return Subject.UNKNOWN;
    }
    final ImportOrigin found = origin.get();
    return found.member().isEmpty()
        ? Subject.module(found.specifier())
        : moduleMember(found.specifier(), found.member());
  }

  private Subject builtinName(final String name) {
    if (index.declaresClass(name)) {
      return Subject.UNKNOWN;
    }
    if (stubs.isClass(name)) {
      return Subject.klass(name);
    }
    return stubs
        .function(PythonQualifiedNames.BUILTINS, name)
        .map(function -> Subject.stubFunction(function, null))
        .orElse(Subject.UNKNOWN);
  }

  private Subject builtinValue(final String name) {
    return index.declaresClass(name) ? Subject.UNKNOWN : Subject.value(TypeRef.of(name));
  }

  private Subject instance(final String name) {
    if (index.declaresClass(name) && stubs.isClass(name)) {
      return Subject.UNKNOWN;
    }
    return Subject.value(TypeRef.of(name));
  }

  /** Resolves a name in the annotation of a declaration of this file. */
  private Optional<TypeRef> sourceName(final SyntaxNode name) {
    final String text = PythonNames.identifier(name);
    final Optional<Binding> binding = index.bindingOf(name);
    if (binding.isEmpty()) {
      return stubs.isClass(text) ? Optional.of(TypeRef.of(text)) : Optional.empty();
    }
    return switch (binding.get().kind()) {
      case IMPORT -> importedType(binding.get().origin());
      case CLASS ->
          index.declaresClass(text) && stubs.isClass(text)
              ? Optional.empty()
              : Optional.of(TypeRef.of(text));
      default -> Optional.empty();
    };
  }

  private Optional<TypeRef> importedType(final Optional<ImportOrigin> origin) {
    if (origin.isEmpty() || origin.get().member().isEmpty()) {
      return Optional.empty();
    }
    final ImportOrigin found = origin.get();
    if (TYPING.equals(found.specifier()) && TYPING_ALIASES.containsKey(found.member())) {
      return Optional.of(TypeRef.of(TYPING_ALIASES.get(found.member())));
    }
    final String qualified = found.specifier() + "." + found.member();
    return stubs.isClass(qualified) ? Optional.of(TypeRef.of(qualified)) : Optional.empty();
  }

  /** Resolves a name in a stub: a class of the same module, or a built-in class. */
  private Optional<TypeRef> stubName(final String module, final SyntaxNode name) {
    final String text = PythonNames.identifier(name);
    final String qualified = PythonQualifiedNames.qualify(module, text);
    if (stubs.isClass(qualified)) {
      return Optional.of(TypeRef.of(qualified));
    }
    return stubs.isClass(text) ? Optional.of(TypeRef.of(text)) : Optional.empty();
  }
}
