package dev.ward.analysis.domain.rule.check;

/** The node kinds and keywords the Pylint-derived checks compare against. */
final class SyntaxKinds {

  static final String CLASSDEF = "classdef";
  static final String FUNCDEF = "funcdef";
  static final String DECORATED = "decorated";
  static final String PARAMETERS = "parameters";

  private SyntaxKinds() {}
}
