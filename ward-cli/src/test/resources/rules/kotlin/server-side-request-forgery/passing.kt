import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestTemplate
import java.net.URL
import io.ktor.client.HttpClient

class Subject {
  fun constant(request: HttpServletRequest, rest: RestTemplate) {
    val unused = request.getParameter("x")
    rest.getForObject("fixed", String::class.java)
  }

  fun overwritten(request: HttpServletRequest, rest: RestTemplate) {
    var value = request.getParameter("x")
    value = "fixed"
    rest.getForObject(value, String::class.java)
  }
}

private val PROVIDERS = setOf("google", "github")

class Providers {
  fun inline(@RequestParam provider: String) {
    if (provider !in PROVIDERS) throw IllegalArgumentException("unsupported")
    URL(provider)
  }

  fun required(@RequestParam provider: String) {
    require(PROVIDERS.contains(provider)) { "unsupported" }
    URL(provider)
  }

  fun viaCallee(@RequestParam provider: String) {
    authorize(provider)
    URL(provider)
  }

  fun authorize(provider: String) {
    if (!PROVIDERS.contains(provider)) {
      throw IllegalArgumentException("unsupported")
    }
  }
}

class KtorClientSafe(private val client: HttpClient) {
  suspend fun fetchFixed() {
    client.get("https://example.test/fixed")
  }
}
