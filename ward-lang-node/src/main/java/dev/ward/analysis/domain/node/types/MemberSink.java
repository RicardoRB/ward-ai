package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects the members, constructors and call signatures a reader finds. */
final class MemberSink {
  private static final String PROPERTY_NAME = "propertyName";

  private final List<Member> members = new ArrayList<>();
  private final Map<String, Member> methods = new LinkedHashMap<>();
  private final List<Sig> constructs = new ArrayList<>();
  private final List<Sig> calls = new ArrayList<>();

  void add(final Member member) {
    members.add(member);
  }

  void addConstruct(final Sig sig) {
    constructs.add(sig);
  }

  void addCall(final Sig sig) {
    calls.add(sig);
  }

  /** The members read so far, with overloads of a method merged. */
  List<Member> members() {
    return List.copyOf(members);
  }

  List<Sig> constructs() {
    return List.copyOf(constructs);
  }

  List<Sig> calls() {
    return List.copyOf(calls);
  }

  /** Adds a method, merging it with an earlier method of the same name as an overload. */
  void method(final String name, final Sig sig, final int flags, final List<String> decorators) {
    final Member existing = methods.get(name);
    if (existing == null) {
      final Member created = new Member(name, new TsType.Fn(sig), List.of(sig), flags, decorators);
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

  /** Adds the index signature declared by {@code node}, if any. */
  void indexSignature(final SyntaxNode node) {
    if (node != null) {
      members.add(
          new Member(
              "[]",
              SigReader.annotated(TreeKit.child(node, "typeAnnotation")),
              List.of(),
              0,
              List.of()));
    }
  }

  /** Reads the members of an enum body. */
  void enumBody(final SyntaxNode body, final String enumName) {
    final SyntaxNode list = body == null ? null : TreeKit.child(body, "enumMemberList");
    if (list == null) {
      return;
    }
    for (final SyntaxNode member : TreeKit.children(list, "enumMember")) {
      final String name = TreeText.unquote(TreeText.text(TreeKit.child(member, PROPERTY_NAME)));
      members.add(
          new Member(
              name,
              TsType.named(enumName + "." + name),
              List.of(),
              Member.READONLY | Member.STATIC,
              List.of()));
    }
  }

  /** Returns the decorator names of a {@code decoratorList}, without {@code @}. */
  static List<String> decorators(final SyntaxNode list) {
    final List<String> names = new ArrayList<>();
    if (list == null) {
      return names;
    }
    for (final SyntaxNode decorator : TreeKit.children(list, "decorator")) {
      final StringBuilder name = new StringBuilder();
      final List<String> leaves = TreeText.leaves(decorator);
      int index = 1;
      while (index < leaves.size() && !"(".equals(leaves.get(index))) {
        name.append(leaves.get(index));
        index++;
      }
      names.add(name.toString());
    }
    return names;
  }
}
