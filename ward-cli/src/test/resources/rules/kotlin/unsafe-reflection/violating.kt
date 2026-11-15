import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest) {
    Class.forName(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    Class.forName(text)
  }

  fun annotated(@RequestParam value: String) {
    Class.forName("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest) {
    val value = read(request)
    Class.forName(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
