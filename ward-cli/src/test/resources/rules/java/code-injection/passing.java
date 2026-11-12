import javax.script.ScriptEngine;
import javax.servlet.http.HttpServletRequest;

class Calculator {
  Object constant(ScriptEngine engine) throws Exception {
    return engine.eval("1 + 2");
  }

  Object numeric(HttpServletRequest request, ScriptEngine engine) throws Exception {
    int value = Integer.parseInt(request.getParameter("value"));
    return engine.eval("var x = " + value + "; x * 2");
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, ScriptEngine engine) throws Exception {
    String formula = read(request);
    return engine.eval(formula);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("formula");
  }
}
