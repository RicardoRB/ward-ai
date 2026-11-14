import java.io.PrintWriter
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, out: PrintWriter) {
    val unused = request.getParameter("x")
    out.println("fixed")
  }

  fun overwritten(request: HttpServletRequest, out: PrintWriter) {
    var value = request.getParameter("x")
    value = "fixed"
    out.println(value)
  }
}
