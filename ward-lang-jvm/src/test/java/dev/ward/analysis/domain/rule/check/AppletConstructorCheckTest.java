package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AppletConstructorCheckTest {

  private static final AppletConstructorCheck CHECK = new AppletConstructorCheck("message");
  private static final String CONSTRUCTOR =
      "  Clock() {\n    String s = getParameter(\"size\");\n  }\n";

  private static List<String> run(final String header, final String members) {
    return new ParsedFacts(header + " {\n" + members + "}\n").run(CHECK);
  }

  @Test
  void aConstructorOfAnAppletThatCallsAStubMethodIsReported() {
    assertThat(run("class Clock extends Applet", CONSTRUCTOR)).containsExactly("3:16");
    assertThat(run("class Clock extends java.applet.Applet", CONSTRUCTOR)).hasSize(1);
    assertThat(
            run(
                "class Clock extends javax.swing.JApplet",
                CONSTRUCTOR.replace("getParameter(\"size\")", "getCodeBase()")))
        .hasSize(1);
  }

  @Test
  void initOtherClassesOtherCallsAndNoSuperclassAreNotReported() {
    assertThat(
            run(
                "class Clock extends Applet",
                "  public void init() {\n    getParameter(\"size\");\n  }\n"))
        .isEmpty();
    assertThat(run("class Clock extends Panel", CONSTRUCTOR)).isEmpty();
    assertThat(
            run(
                "class Clock extends Applet",
                CONSTRUCTOR.replace("getParameter(\"size\")", "toString()")))
        .isEmpty();
    assertThat(run("class Clock", CONSTRUCTOR)).isEmpty();
  }
}
