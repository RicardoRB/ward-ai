package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The methods and fields of one Java file with the nullness qualifiers they are annotated with, so
 * a call, a return or a field store can be checked against the declaration it meets in this file. A
 * declaration in another file is unknown.
 */
final class FlowNullIndex {

  private static final String FIELD = "fieldDeclaration";
  private static final String TYPE_TYPE = "typeType";
  private static final String IDENTIFIER = "identifier";
  private static final Pattern OPTIONAL = Pattern.compile("^(java\\.util\\.)?Optional<");

  private final List<Method> methods = new ArrayList<>();
  private final List<Field> fields = new ArrayList<>();

  /** A method or constructor of the file. */
  record Method(
      String name,
      Span span,
      List<String> paramNames,
      List<FlowNullQual> params,
      FlowNullQual returns,
      boolean optionalReturn) {

    /** Copies the lists. */
    Method {
      paramNames = List.copyOf(paramNames);
      params = List.copyOf(params);
    }
  }

  /** A field of the file. */
  record Field(String name, FlowNullQual qual) {}

  private FlowNullIndex() {}

  /** Reads the declarations of the file whose syntax tree is {@code root}. */
  static FlowNullIndex of(final SyntaxNode root) {
    final FlowNullIndex index = new FlowNullIndex();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(
            root, Set.of("methodDeclaration", "constructorDeclaration", "fieldDeclaration"))) {
      final boolean marked = FlowNullNotes.marked(visit.ancestors());
      final SyntaxNode node = visit.node();
      if (FIELD.equals(node.kind())) {
        index.field(visit, marked);
      } else {
        index.method(visit, marked);
      }
    }
    return index;
  }

  /**
   * Returns the method called {@code name} with {@code arity} parameters, if it is the only one.
   */
  Optional<Method> method(final String name, final int arity) {
    final List<Method> found =
        methods.stream()
            .filter(method -> method.name().equals(name) && method.params().size() == arity)
            .toList();
    return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
  }

  /** Returns the innermost method whose source contains {@code span}. */
  Optional<Method> enclosing(final Span span) {
    Method best = null;
    for (final Method method : methods) {
      final boolean contains =
          method.span().startOffset() <= span.startOffset()
              && span.endOffset() <= method.span().endOffset();
      if (contains && (best == null || method.span().startOffset() > best.span().startOffset())) {
        best = method;
      }
    }
    return Optional.ofNullable(best);
  }

  /** Returns the qualifier of the field {@code name}, if the file declares it once. */
  Optional<FlowNullQual> field(final String name) {
    final List<Field> found = fields.stream().filter(field -> field.name().equals(name)).toList();
    return found.size() == 1 ? Optional.of(found.get(0).qual()) : Optional.empty();
  }

  private void field(final SyntaxWalk.Visit visit, final boolean marked) {
    final List<SyntaxNode> notes = FlowNullNotes.notes(visit);
    SyntaxWalk.firstDescendant(visit.node(), TYPE_TYPE, Set.of())
        .ifPresent(type -> notes.addAll(FlowNullNotes.annotations(type)));
    final FlowNullQual qual = FlowNullQual.of(notes, marked);
    for (final SyntaxWalk.Visit declarator :
        SyntaxWalk.visits(visit.node(), Set.of("variableDeclaratorId"))) {
      fields.add(new Field(identifier(declarator.node()), qual));
    }
  }

  private void method(final SyntaxWalk.Visit visit, final boolean marked) {
    final SyntaxNode node = visit.node();
    final List<SyntaxNode> notes = FlowNullNotes.notes(visit);
    final Optional<SyntaxNode> returned =
        SyntaxWalk.firstDescendant(node, "typeTypeOrVoid", Set.of("block", "formalParameters"))
            .flatMap(type -> SyntaxWalk.firstDescendant(type, TYPE_TYPE, Set.of()));
    returned.ifPresent(type -> notes.addAll(FlowNullNotes.annotations(type)));
    final List<String> names = new ArrayList<>();
    final List<FlowNullQual> quals = new ArrayList<>();
    for (final SyntaxWalk.Visit param : SyntaxWalk.visits(node, Set.of("formalParameter"))) {
      names.add(identifier(param.node()));
      quals.add(FlowNullQual.of(FlowNullNotes.ofParameter(param.node()), marked));
    }
    final boolean constructor = "constructorDeclaration".equals(node.kind());
    final boolean optional =
        returned.map(type -> OPTIONAL.matcher(SyntaxWalk.text(type)).find()).orElse(false);
    methods.add(
        new Method(
            identifier(node),
            node.span(),
            names,
            quals,
            constructor ? FlowNullQual.NONE : FlowNullQual.of(notes, marked),
            optional));
  }

  /** The name a method, field or parameter declares. */
  private static String identifier(final SyntaxNode node) {
    final Optional<SyntaxNode> own =
        node.children().stream().filter(child -> IDENTIFIER.equals(child.kind())).findFirst();
    return own.or(
            () ->
                SyntaxWalk.firstDescendant(
                        node, "variableDeclaratorId", Set.of("block", "typeType"))
                    .flatMap(id -> SyntaxWalk.firstDescendant(id, IDENTIFIER, Set.of())))
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
