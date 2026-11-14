package dev.ward.analysis.domain.android;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class XmlParserTest {

  private static final int DEEP = 500;
  private static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

  private static XmlDiagnostic problem(final String text) {
    final XmlResult result = XmlParser.parse(text);
    assertThat(result.tree().kind()).isEqualTo(AndroidSyntax.DOCUMENT);
    assertThat(result.tree().children()).isEmpty();
    return result.diagnostics().getFirst();
  }

  private static String at(final SyntaxNode node) {
    return node.span().startLine() + ":" + node.span().startColumn();
  }

  @Test
  void anElementHasItsAttributesFirstThenItsChildrenWithPositions() {
    final String text =
        DECLARATION
            + "<manifest xmlns:android=\"urn:a\">\n"
            + "  <application android:label='x'/>\n"
            + "</manifest>\n";

    final XmlResult result = XmlParser.parse(text);

    assertThat(result.diagnostics()).isEmpty();
    final SyntaxNode root = result.tree();
    assertThat(root.fields()).containsEntry(AndroidSyntax.NAME, "manifest");
    assertThat(at(root)).isEqualTo("2:1");
    assertThat(root.span().endLine()).isEqualTo(root.span().startLine() + 2);
    final SyntaxNode xmlns = root.children().get(0);
    assertThat(xmlns.kind()).isEqualTo(AndroidSyntax.ATTRIBUTE);
    assertThat(xmlns.fields())
        .containsEntry(AndroidSyntax.PREFIX, "xmlns")
        .containsEntry(AndroidSyntax.LOCAL, "android")
        .containsEntry(AndroidSyntax.VALUE, "urn:a");
    final SyntaxNode application = root.children().get(1);
    assertThat(at(application)).isEqualTo("3:3");
    final SyntaxNode label = application.children().getFirst();
    assertThat(label.fields()).containsEntry(AndroidSyntax.VALUE, "x");
    assertThat(at(label)).isEqualTo("3:16");
  }

  @Test
  void anAttributeWithoutPrefixHasAnEmptyPrefix() {
    final SyntaxNode attribute = XmlParser.parse("<a b=\"1\"/>").tree().children().getFirst();

    assertThat(attribute.fields())
        .containsEntry(AndroidSyntax.PREFIX, "")
        .containsEntry(AndroidSyntax.LOCAL, "b");
  }

  @Test
  void textCdataEntitiesAndCommentsAreRead() {
    final String text =
        "﻿<!-- c --><resources><?pi x?><string a=\"&lt;&#65;&#x42;&amp;\">Hi &quot;you&quot;</string>"
            + "<string><![CDATA[a < b]]></string>   <!-- t --></resources>";

    final SyntaxNode root = XmlParser.parse(text).tree();

    final SyntaxNode first = root.children().get(0);
    assertThat(first.children().get(0).fields()).containsEntry(AndroidSyntax.VALUE, "<AB&");
    assertThat(first.children().get(1).fields()).containsEntry(AndroidSyntax.VALUE, "Hi \"you\"");
    final SyntaxNode second = root.children().get(1);
    assertThat(second.children().getFirst().fields()).containsEntry(AndroidSyntax.VALUE, "a < b");
    assertThat(root.children()).hasSize(2);
  }

  @Test
  void lineBreaksOfEveryKindCountOnce() {
    final SyntaxNode root = XmlParser.parse("<a>\r\n<b/>\r<c/>\n<d/></a>").tree();

    assertThat(root.children().stream().map(XmlParserTest::at).toList())
        .containsExactly("2:1", "3:1", "4:1");
  }

  @Test
  void aProblemIsADiagnosticWithItsPositionAndNeverAnException() {
    assertThat(problem("").message()).isEqualTo("expected the root element");
    assertThat(problem("<a>\n  <b>\n</a>"))
        .satisfies(
            found -> {
              assertThat(found.message()).isEqualTo("end tag does not match b");
              assertThat(found.line() + ":" + found.column()).isEqualTo("3:1");
            });
    assertThat(problem("<a").message()).isEqualTo("unterminated start tag");
    assertThat(problem("<a><b></a>").message()).contains("does not match");
    assertThat(problem("<a/><b/>").message()).contains("after the root");
    assertThat(problem("<a/>x").message()).contains("after the root");
    assertThat(problem("x<a/>").message()).contains("root element");
    assertThat(problem("<a b=1/>").message()).contains("quoted");
    assertThat(problem("<a b=\"1/>").message()).contains("unterminated");
    assertThat(problem("<a b=\"1\" b=\"2\"/>").message()).isEqualTo("duplicate attribute");
    assertThat(problem("<a>&nbsp;</a>").message()).contains("unknown entity");
    assertThat(problem("<a>&amp</a>").message()).contains("unterminated reference");
    assertThat(problem("<a>&#xZZ;</a>").message()).contains("invalid character");
    assertThat(problem("<a><!-- x</a>").message()).contains("comment");
    assertThat(problem("<a><![CDATA[x</a>").message()).contains("CDATA");
    assertThat(problem("<?xml").message()).contains("processing instruction");
    assertThat(problem("<a><?x</a>").message()).contains("processing instruction");
    assertThat(problem("<a>").message()).contains("unterminated element");
    assertThat(problem("<a b").message()).isEqualTo("expected '='");
    assertThat(problem("<a b \"1\"/>").message()).contains("expected '='");
    assertThat(problem("<>").message()).isEqualTo("expected a name");
  }

  @Test
  void aDoctypeIsRejectedSoNoEntityIsEverExpanded() {
    final XmlDiagnostic found =
        problem("<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>");

    assertThat(found.message()).isEqualTo("a DOCTYPE is not supported");
    assertThat(found.line() + ":" + found.column()).isEqualTo("1:1");
  }

  @Test
  void deepNestingIsRefusedBeforeTheStackRunsOut() {
    final String text = "<a>".repeat(DEEP) + "</a>".repeat(DEEP);

    assertThat(problem(text).message()).isEqualTo("elements are nested too deeply");
  }
}
