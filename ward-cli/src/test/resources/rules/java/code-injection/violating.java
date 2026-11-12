import javax.script.ScriptEngine;
import javax.servlet.http.HttpServletRequest;

class Calculator {
  Object direct(HttpServletRequest request, ScriptEngine engine) throws Exception {
    return engine.eval(request.getParameter("formula"));
  }

  Object propagated(HttpServletRequest request, ScriptEngine engine) throws Exception {
    String value = request.getParameter("value");
    String script = "var x = " + value + "; x * 2";
    return engine.eval(script);
  }
}
