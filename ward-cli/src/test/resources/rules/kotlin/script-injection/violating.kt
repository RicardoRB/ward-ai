import javax.script.ScriptEngine
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, engine: ScriptEngine) {
    engine.eval(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, engine: ScriptEngine) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    engine.eval(text)
  }

  fun annotated(@RequestParam value: String, engine: ScriptEngine) {
    engine.eval("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, engine: ScriptEngine) {
    val value = read(request)
    engine.eval(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
