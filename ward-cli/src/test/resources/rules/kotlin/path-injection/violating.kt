import java.io.File
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest) {
    File(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    File(text)
  }

  fun annotated(@RequestParam value: String) {
    File("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest) {
    val value = read(request)
    File(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
