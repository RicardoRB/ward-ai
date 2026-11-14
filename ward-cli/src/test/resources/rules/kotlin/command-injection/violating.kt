import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest) {
    Runtime.getRuntime().exec(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    Runtime.getRuntime().exec(text)
  }

  fun annotated(@RequestParam value: String) {
    Runtime.getRuntime().exec("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest) {
    val value = read(request)
    Runtime.getRuntime().exec(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")

  fun insideLet(request: HttpServletRequest) {
    request.getParameter("x")?.let { Runtime.getRuntime().exec(it) }
  }

  fun fromWhen(request: HttpServletRequest, mode: Int) {
    val value = when (mode) {
      1 -> "fixed"
      else -> request.getParameter("x")
    }
    Runtime.getRuntime().exec(value)
  }

  fun fromElvis(request: HttpServletRequest) {
    val value = request.getParameter("x") ?: "fixed"
    Runtime.getRuntime().exec(value)
  }

  fun fromProperty(request: HttpServletRequest) {
    Runtime.getRuntime().exec(request.queryString)
  }

  val started = Runtime.getRuntime().exec(readLine())

  val running: Process
    get() = Runtime.getRuntime().exec(readLine())
}
