package dev.ward.analysis.domain.flow.taint;

/** What a scenario changes of the proven shape. */
record MemberVariant(
    boolean marksMember,
    boolean otherReturn,
    boolean aliasHandedOn,
    boolean secondAssignment
) {
  static MemberVariant proven() {
    return new MemberVariant(true, false, false, false);
  }

  MemberVariant withoutMember() {
    return new MemberVariant(false, otherReturn, aliasHandedOn, secondAssignment);
  }

  MemberVariant withOtherReturn() {
    return new MemberVariant(marksMember, true, aliasHandedOn, secondAssignment);
  }

  MemberVariant withHandedOnAlias() {
    return new MemberVariant(marksMember, otherReturn, true, secondAssignment);
  }

  MemberVariant withSecondAssignment() {
    return new MemberVariant(marksMember, otherReturn, aliasHandedOn, true);
  }
}
