import javax.script.ScriptEngine
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, engine: ScriptEngine) {
    val unused = request.getParameter("x")
    engine.eval("fixed")
  }

  fun overwritten(request: HttpServletRequest, engine: ScriptEngine) {
    var value = request.getParameter("x")
    value = "fixed"
    engine.eval(value)
  }
}
