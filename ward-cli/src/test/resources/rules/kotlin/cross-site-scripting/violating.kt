import java.io.PrintWriter
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, out: PrintWriter) {
    out.println(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, out: PrintWriter) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    out.println(text)
  }

  fun annotated(@RequestParam value: String, out: PrintWriter) {
    out.println("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, out: PrintWriter) {
    val value = read(request)
    out.println(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
