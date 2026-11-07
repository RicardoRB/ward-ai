package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the members of classes, interfaces and enums, and the parts of a class header. */
final class MemberReader {
  private static final String PROPERTY_NAME = "propertyName";
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String INITIALIZER = "initializer";
  private static final String QUESTION = "?";
  private static final String CONSTRUCTOR = "constructor";

  private final List<Member> members = new ArrayList<>();
  private final Map<String, Member> methods = new LinkedHashMap<>();
  private final List<Sig> constructs = new ArrayList<>();
  private final List<Sig> calls = new ArrayList<>();

  /** The members read so far, with overloads of a method merged. */
  List<Member> members() {
    return List.copyOf(members);
  }

  /** The construct signatures read so far. */
  List<Sig> constructs() {
    return List.copyOf(constructs);
  }

  /** The call signatures read so far. */
  List<Sig> calls() {
    return List.copyOf(calls);
  }

  /** Reads the elements of a {@code classTail}. */
  void classTail(final SyntaxNode tail) {
    for (final SyntaxNode element : TreeKit.children(tail, "classElement")) {
      final List<String> decorators = decorators(TreeKit.child(element, "decoratorList"));
      for (final SyntaxNode part : TreeKit.parts(element)) {
        switch (part.kind()) {
          case "constructorDeclaration" -> constructor(part);
          case "propertyMemberDeclaration" -> classMember(part, decorators);
          case "indexMemberDeclaration" -> indexSignature(TreeKit.child(part, "indexSignature"));
          default -> {
            // decorators were read above; statements in a class body declare no members
          }
        }
      }
    }
  }

  /** Reads the members of an interface or object type. */
  void objectType(final SyntaxNode objectType) {
    final SyntaxNode body = TreeKit.child(objectType, "typeBody");
    final SyntaxNode list = body == null ? null : TreeKit.child(body, "typeMemberList");
    if (list != null) {
      TreeKit.children(list, "typeMember").forEach(this::typeMember);
    }
  }

  /** Reads the members of an enum body. */
  void enumBody(final SyntaxNode body, final String enumName) {
    final SyntaxNode list = body == null ? null : TreeKit.child(body, "enumMemberList");
    if (list != null) {
      for (final SyntaxNode member : TreeKit.children(list, "enumMember")) {
        final String name = TreeKit.unquote(TreeKit.text(TreeKit.child(member, PROPERTY_NAME)));
        members.add(
            new Member(
                name,
                Ty.named(enumName + "." + name),
                List.of(),
                Member.READONLY | Member.STATIC,
                List.of()));
      }
    }
  }

  /** Returns the decorator names of a {@code decoratorList}, without {@code @}. */
  static List<String> decorators(final SyntaxNode list) {
    final List<String> names = new ArrayList<>();
    if (list != null) {
      for (final SyntaxNode decorator : TreeKit.children(list, "decorator")) {
        final List<String> leaves = TreeKit.leaves(decorator);
        final StringBuilder name = new StringBuilder();
        for (final String leaf : leaves.subList(1, leaves.size())) {
          if ("(".equals(leaf)) {
            break;
          }
          name.append(leaf);
        }
        names.add(name.toString());
      }
    }
    return names;
  }

  private void typeMember(final SyntaxNode member) {
    for (final SyntaxNode part : TreeKit.parts(member)) {
      switch (part.kind()) {
        case "propertySignatur" -> propertySignature(part);
        case "methodSignature" -> methodSignature(part);
        case CALL_SIGNATURE -> calls.add(SigReader.of(part));
        case "constructSignature" -> constructs.add(SigReader.of(part));
        case "indexSignature" -> indexSignature(part);
        case "getter" -> accessor(member);
        default -> {
          // separators and setters add nothing the getter did not
        }
      }
    }
  }

  private void accessor(final SyntaxNode member) {
    final SyntaxNode getter = TreeKit.child(member, "getter");
    final String name = TreeKit.text(TreeKit.child(getter, "classElementName"));
    members.add(new Member(name, SigReader.returns(member), List.of(), Member.READONLY, List.of()));
  }

  private void propertySignature(final SyntaxNode node) {
    final int flags =
        (TreeKit.hasToken(node, "readonly") ? Member.READONLY : 0)
            | (TreeKit.hasToken(node, QUESTION) ? Member.OPTIONAL : 0);
    members.add(
        new Member(
            TreeKit.unquote(TreeKit.text(TreeKit.child(node, PROPERTY_NAME))),
            SigReader.annotated(TreeKit.child(node, TYPE_ANNOTATION)),
            List.of(),
            flags,
            List.of()));
  }

  private void methodSignature(final SyntaxNode node) {
    final int flags = TreeKit.hasToken(node, QUESTION) ? Member.OPTIONAL : 0;
    method(
        TreeKit.unquote(TreeKit.text(TreeKit.child(node, PROPERTY_NAME))),
        SigReader.of(TreeKit.child(node, CALL_SIGNATURE)),
        flags,
        List.of());
  }

  private void indexSignature(final SyntaxNode node) {
    if (node != null) {
      members.add(
          new Member(
              "[]",
              SigReader.annotated(TreeKit.child(node, TYPE_ANNOTATION)),
              List.of(),
              0,
              List.of()));
    }
  }

  private void constructor(final SyntaxNode node) {
    final Sig sig = new Sig(List.of(), SigReader.params(node), Ty.UNKNOWN);
    constructs.add(sig);
    for (final SyntaxNode list : TreeKit.children(node, "formalParameterList")) {
      for (final SyntaxNode argument : TreeKit.children(list, "formalParameterArg")) {
        parameterProperty(argument);
      }
    }
  }

  private void parameterProperty(final SyntaxNode argument) {
    final SyntaxNode modifier = TreeKit.child(argument, "accessibilityModifier");
    if (modifier != null) {
      final int hidden = "public".equals(TreeKit.text(modifier)) ? 0 : Member.HIDDEN;
      final Param param = SigReader.params(wrap(argument)).get(0);
      members.add(new Member(param.name(), param.type(), List.of(), hidden, List.of()));
    }
  }

  private static SyntaxNode wrap(final SyntaxNode argument) {
    return new SyntaxNode("formalParameterList", argument.span(), Map.of(), List.of(argument));
  }

  private void classMember(final SyntaxNode node, final List<String> decorators) {
    final SyntaxNode base = TreeKit.child(node, "propertyMemberBase");
    final int flags = base == null ? 0 : modifiers(TreeKit.leaves(base));
    final SyntaxNode name = TreeKit.child(node, PROPERTY_NAME);
    final SyntaxNode signature = TreeKit.child(node, CALL_SIGNATURE);
    if (name != null && signature != null) {
      method(TreeKit.unquote(TreeKit.text(name)), SigReader.of(signature), flags, decorators);
    } else if (name != null) {
      property(node, name, flags, decorators);
    } else if (TreeKit.child(node, "getAccessor") != null) {
      accessor(TreeKit.child(node, "getAccessor"), flags, decorators);
    } else if (TreeKit.child(node, "abstractDeclaration") != null) {
      abstractMember(TreeKit.child(node, "abstractDeclaration"), decorators);
    }
  }

  private void property(
      final SyntaxNode node,
      final SyntaxNode name,
      final int flags,
      final List<String> decorators) {
    final int all = flags | (TreeKit.hasToken(node, QUESTION) ? Member.OPTIONAL : 0);
    Ty type = SigReader.annotated(TreeKit.child(node, TYPE_ANNOTATION));
    final SyntaxNode initializer = TreeKit.child(node, INITIALIZER);
    if (TreeKit.child(node, TYPE_ANNOTATION) == null && initializer != null) {
      type =
          SimpleInit.of(
              TreeKit.child(initializer, "singleExpression"), (all & Member.READONLY) != 0);
    }
    members.add(new Member(TreeKit.unquote(TreeKit.text(name)), type, List.of(), all, decorators));
  }

  private void accessor(final SyntaxNode node, final int flags, final List<String> decorators) {
    final SyntaxNode getter = TreeKit.child(node, "getter");
    final String name = TreeKit.text(TreeKit.child(getter, "classElementName"));
    members.add(new Member(name, SigReader.returns(node), List.of(), flags, decorators));
  }

  private void abstractMember(final SyntaxNode node, final List<String> decorators) {
    final SyntaxNode name = TreeKit.child(node, "identifier");
    final SyntaxNode signature = TreeKit.child(node, CALL_SIGNATURE);
    if (name != null && signature != null) {
      method(TreeKit.text(name), SigReader.of(signature), Member.ABSTRACT, decorators);
    }
  }

  private void method(
      final String name, final Sig sig, final int flags, final List<String> decorators) {
    final Member existing = methods.get(name);
    if (existing == null) {
      final Member created = new Member(name, new Ty.Fn(sig), List.of(sig), flags, decorators);
      methods.put(name, created);
      members.add(created);
    } else {
      final List<Sig> sigs = new ArrayList<>(existing.sigs());
      sigs.add(sig);
      final Member merged =
          new Member(name, existing.type(), sigs, existing.flags(), existing.decorators());
      methods.put(name, merged);
      members.set(members.indexOf(existing), merged);
    }
  }

  private static int modifiers(final List<String> tokens) {
    int flags = 0;
    for (final String token : tokens) {
      flags |=
          switch (token) {
            case "static" -> Member.STATIC;
            case "readonly" -> Member.READONLY;
            case "abstract" -> Member.ABSTRACT;
            case "private", "protected" -> Member.HIDDEN;
            default -> 0;
          };
    }
    return flags;
  }

  /** Returns whether {@code name} is the name of a constructor. */
  static boolean isConstructor(final String name) {
    return CONSTRUCTOR.equals(name);
  }
}
