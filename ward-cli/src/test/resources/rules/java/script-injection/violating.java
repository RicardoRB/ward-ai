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

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, ScriptEngine engine) throws Exception {
    String formula = read(request);
    return engine.eval(formula);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("formula");
  }
}
