package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;

/** A metric rule ready to run: it visits every node of a tree and measures the target kinds. */
final class MetricCompiledRule implements CompiledRule {

  private static final String CLASS = "classdef";
  private static final String FUNCTION = "funcdef";
  private static final String LAMBDA = "lambdef";

  private final RuleDefinition definition;
  private final MetricRule rule;

  MetricCompiledRule(final RuleDefinition definition, final MetricRule rule) {
    this.definition = definition;
    this.rule = rule;
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<Measured> measures(final SyntaxNode root) {
    final List<Measured> measured = new ArrayList<>();
    collect(root, false, measured);
    return List.copyOf(measured);
  }

  private void collect(final SyntaxNode node, final boolean method, final List<Measured> measured) {
    for (final MetricRule.Target target : rule.targets()) {
      if (node.kind().equals(target.kind())) {
        rule.measurement()
            .measure()
            .measured(node, target, method, rule)
            .map(violation -> new Measured(violation.span(), violation.value()))
            .ifPresent(measured::add);
      }
    }
    final boolean inClass = inClassBody(node, method);
    for (final SyntaxNode child : node.children()) {
      collect(child, inClass, measured);
    }
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    visit(root, false, matches);
    return List.copyOf(matches);
  }

  private void visit(final SyntaxNode node, final boolean method, final List<RuleMatch> matches) {
    for (final MetricRule.Target target : rule.targets()) {
      if (node.kind().equals(target.kind())) {
        measure(node, target, method, matches);
      }
    }
    final boolean inClass = inClassBody(node, method);
    for (final SyntaxNode child : node.children()) {
      visit(child, inClass, matches);
    }
  }

  private void measure(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final List<RuleMatch> matches) {
    rule.measurement()
        .measure()
        .check(node, target, method, rule)
        .map(this::match)
        .ifPresent(matches::add);
  }

  private RuleMatch match(final Violation violation) {
    final String message =
        rule.message()
            .replace("{name}", violation.name())
            .replace("{value}", String.valueOf(violation.value()))
            .replace("{max}", String.valueOf(rule.max()))
            .replace("{expected}", violation.expected());
    return new RuleMatch(violation.span(), message);
  }

  /** A function is a method when its parent is a class body; nested scopes start afresh. */
  private static boolean inClassBody(final SyntaxNode node, final boolean method) {
    if (CLASS.equals(node.kind())) {
      return true;
    }
    if (FUNCTION.equals(node.kind()) || LAMBDA.equals(node.kind())) {
      return false;
    }
    return method;
  }
}
