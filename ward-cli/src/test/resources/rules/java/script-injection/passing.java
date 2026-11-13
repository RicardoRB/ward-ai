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
}
