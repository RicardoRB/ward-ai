import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, response: HttpServletResponse) {
    val unused = request.getParameter("x")
    response.sendRedirect("fixed")
  }

  fun overwritten(request: HttpServletRequest, response: HttpServletResponse) {
    var value = request.getParameter("x")
    value = "fixed"
    response.sendRedirect(value)
  }
}

class KtorRedirectSafe {
  fun go() {
    val target = call.request.queryParameters["to"]
    call.respondRedirect("/home")
  }
}

class KtorRedirectCounted {
  fun next() {
    val n = (call.parameters["n"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
    call.respondRedirect("/relative-redirect/${n - 1}")
  }
}
