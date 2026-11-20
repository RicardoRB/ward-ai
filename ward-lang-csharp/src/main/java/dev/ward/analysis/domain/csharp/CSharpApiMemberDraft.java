package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** A member being read: collects what the declaration says, then maps it to an {@link ApiMember}. */
final class CSharpApiMemberDraft {
  private static final String OVERRIDE = "override";

  private final ApiMember.Kind kind;
  private final String name;
  private final CSharpApiModifiers mods;
  private final CSharpApiOwner owner;
  private final ApiLocation location;
  private List<ApiTypeParameter> typeParameters = List.of();
  private List<ApiParameter> parameters = List.of();
  private ApiTypeRef type = CSharpApiRefs.VOID;
  private boolean varargs;
  private boolean bodyless;
  private boolean constant;

  CSharpApiMemberDraft(
      final ApiMember.Kind kind,
      final String name,
      final CSharpApiModifiers mods,
      final CSharpApiOwner owner,
      final SyntaxNode at
  ) {
    this.kind = kind;
    this.name = name;
    this.mods = mods;
    this.owner = owner;
    this.location = new ApiLocation(owner.path(), at.span().startLine(), at.span().startColumn());
  }

  CSharpApiMemberDraft signature(
      final List<ApiTypeParameter> newTypeParameters,
      final List<ApiParameter> newParameters,
      final ApiTypeRef newType
  ) {
    this.typeParameters = List.copyOf(newTypeParameters);
    this.parameters = List.copyOf(newParameters);
    this.type = newType;
    return this;
  }

  CSharpApiMemberDraft varargs(final boolean value) {
    this.varargs = value;
    return this;
  }

  /** Marks a member that has no body, so an interface member of it is abstract. */
  CSharpApiMemberDraft bodyless(final boolean value) {
    this.bodyless = value;
    return this;
  }

  /** Marks a constant: a static field that cannot be reassigned. */
  CSharpApiMemberDraft constant() {
    this.constant = true;
    return this;
  }

  ApiMember build() {
    final boolean isStatic = constant || mods.has("static");
    final boolean method = kind == ApiMember.Kind.METHOD;
    final boolean interfaceBody = owner.isInterface() && method && !isStatic;
    return new ApiMember(
        kind,
        name,
        mods.visibility(owner.defaultVisibility()),
        isStatic,
        isFinal(isStatic),
        mods.has("abstract") || interfaceBody && bodyless,
        interfaceBody && !bodyless,
        varargs,
        typeParameters,
        parameters,
        type,
        location
    );
  }

  private boolean isFinal(final boolean isStatic) {
    if (kind == ApiMember.Kind.FIELD) {
      return constant || mods.has("readonly");
    }
    if (kind != ApiMember.Kind.METHOD || isStatic || owner.isInterface()) {
      return false;
    }
    return owner.isStruct()
        || mods.has("sealed")
        || !(mods.has("virtual") || mods.has("abstract") || mods.has(OVERRIDE));
  }
}
