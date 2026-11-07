package fixture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Loader(private val name: String, private val io: Any = Dispatchers.IO) {
    suspend fun load(): String = withContext(io) {
        name
    }
}
