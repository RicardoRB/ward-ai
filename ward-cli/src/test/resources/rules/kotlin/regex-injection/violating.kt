import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest) {
    Pattern.compile(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    Pattern.compile(text)
  }

  fun annotated(@RequestParam value: String) {
    Pattern.compile("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest) {
    val value = read(request)
    Pattern.compile(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
