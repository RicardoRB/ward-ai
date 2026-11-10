package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a construct signature or a method that reads as an instantiator or a constructor in an
 * interface, class or type alias, at the member, as Biome 2.5.15's {@code noMisleadingInstantiator}
 * does. An interface or class reports its first such member; a type alias reports only when its
 * first method is named constructor.
 */
public final class BiomeMisleadingInstantiatorCheck implements RuleCheck {

  private static final String INTERFACE = "interfaceDeclaration";
  private static final String CLASS = "classDeclaration";
  private static final String TYPE_ALIAS = "typeAliasDeclaration";
  private static final String TYPE_MEMBER = "typeMember";
  private static final String CONSTRUCT = "constructSignature";
  private static final String METHOD = "methodSignature";
  private static final String NEW = "new";
  private static final String CONSTRUCTOR = "constructor";
  private static final String THIS = "this";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE = "type_";
  private static final String ANNOTATION = "typeAnnotation";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String BODY = "functionBody";
  private static final String CLASS_MEMBER = "propertyMemberDeclaration";
  private static final String NAME = "propertyName";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeMisleadingInstantiatorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the return types are read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the misleading-instantiator check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(INTERFACE, CLASS, TYPE_ALIAS)).stream()
        .flatMap(visit -> finding(visit.node()).stream())
        .map(member -> CheckSupport.at(member.span(), message))
        .toList();
  }

  private static Optional<SyntaxNode> finding(final SyntaxNode declaration) {
    return switch (declaration.kind()) {
      case INTERFACE -> interfaceFinding(declaration);
      case CLASS -> classFinding(declaration);
      default -> aliasFinding(declaration);
    };
  }

  /** The first construct signature that returns the interface or this, or a constructor method. */
  private static Optional<SyntaxNode> interfaceFinding(final SyntaxNode interfaceNode) {
    final String name = textOf(interfaceNode, IDENTIFIER);
    return members(interfaceNode).stream()
        .filter(
            member ->
                childOfKind(member, CONSTRUCT)
                    .map(construct -> returnsInstance(construct, name))
                    .orElseGet(
                        () ->
                            childOfKind(member, METHOD)
                                .filter(method -> CONSTRUCTOR.equals(textOf(method, NAME)))
                                .isPresent()))
        .findFirst();
  }

  /** The first new member, with no body, that returns the class or this. */
  private static Optional<SyntaxNode> classFinding(final SyntaxNode classNode) {
    final String name = textOf(classNode, IDENTIFIER);
    return classNode.children().stream()
        .filter(child -> "classTail".equals(child.kind()))
        .flatMap(tail -> tail.children().stream())
        .filter(element -> "classElement".equals(element.kind()))
        .flatMap(element -> element.children().stream())
        .filter(member -> CLASS_MEMBER.equals(member.kind()))
        .filter(member -> NEW.equals(textOf(member, NAME)))
        .filter(member -> childOfKind(member, BODY).isEmpty())
        .filter(member -> childOfKind(member, CALL_SIGNATURE).isPresent())
        .filter(member -> returnsInstance(childOfKind(member, CALL_SIGNATURE).get(), name))
        .findFirst();
  }

  /** A type alias reports only when the first method of its object type is named constructor. */
  private static Optional<SyntaxNode> aliasFinding(final SyntaxNode alias) {
    return alias.children().stream()
        .filter(child -> TYPE.equals(child.kind()))
        .findFirst()
        .flatMap(
            type ->
                members(type).stream()
                    .filter(member -> childOfKind(member, METHOD).isPresent())
                    .findFirst())
        .filter(member -> CONSTRUCTOR.equals(textOf(childOfKind(member, METHOD).get(), NAME)))
        .map(member -> childOfKind(member, METHOD).get());
  }

  /** Whether the signature's return type is the declaring name, optionally generic, or this. */
  private static boolean returnsInstance(final SyntaxNode signature, final String name) {
    return childOfKind(signature, ANNOTATION)
        .flatMap(annotation -> childOfKind(annotation, TYPE))
        .map(SyntaxWalk::text)
        .filter(text -> THIS.equals(text) || text.equals(name) || text.startsWith(name + "<"))
        .isPresent();
  }

  /** The members of the declaration's body, in order: the interface's or the object type's. */
  private static List<SyntaxNode> members(final SyntaxNode node) {
    return SyntaxWalk.visits(node, Set.of(TYPE_MEMBER)).stream()
        .map(SyntaxWalk.Visit::node)
        .toList();
  }

  /** The text of the named child of a node, or empty when there is none. */
  private static String textOf(final SyntaxNode node, final String kind) {
    return childOfKind(node, kind).map(SyntaxWalk::text).orElse("");
  }

  private static Optional<SyntaxNode> childOfKind(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }
}
