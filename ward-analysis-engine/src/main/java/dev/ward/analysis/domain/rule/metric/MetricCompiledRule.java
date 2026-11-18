package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    collect(root, false, new ArrayDeque<>(), measured);
    return List.copyOf(measured);
  }

  private void collect(
      final SyntaxNode node,
      final boolean method,
      final Deque<SyntaxNode> ancestors,
      final List<Measured> measured
  ) {
    for (final MetricRule.Target target : rule.targets()) {
      if (node.kind().equals(target.kind()) && !ExemptPaths.covers(
          rule.keys().exempt(),
          ancestors
      )) {
        rule.measurement()
            .measure()
            .measured(node, target, method, rule)
            .map(MetricCompiledRule::measured)
            .ifPresent(measured::add);
      }
    }
    final boolean inClass = inClassBody(node, method);
    ancestors.push(node);
    for (final SyntaxNode child : node.children()) {
      collect(child, inClass, ancestors, measured);
    }
    ancestors.pop();
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    visit(root, false, new ArrayDeque<>(), matches);
    return List.copyOf(matches);
  }

  private void visit(
      final SyntaxNode node,
      final boolean method,
      final Deque<SyntaxNode> ancestors,
      final List<RuleMatch> matches
  ) {
    for (final MetricRule.Target target : rule.targets()) {
      if (node.kind().equals(target.kind()) && !ExemptPaths.covers(
          rule.keys().exempt(),
          ancestors
      )) {
        measure(node, target, method, matches);
      }
    }
    final boolean inClass = inClassBody(node, method);
    ancestors.push(node);
    for (final SyntaxNode child : node.children()) {
      visit(child, inClass, ancestors, matches);
    }
    ancestors.pop();
  }

  private void measure(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final List<RuleMatch> matches
  ) {
    rule.measurement()
        .measure()
        .check(node, target, method, rule)
        .map(this::match)
        .ifPresent(matches::add);
  }

  private static Measured measured(final Violation violation) {
    return new Measured(violation.span(), violation.value());
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
