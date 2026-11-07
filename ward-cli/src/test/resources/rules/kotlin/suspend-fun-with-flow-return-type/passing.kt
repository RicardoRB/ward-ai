package fixture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun numbers(): Flow<Int> = flowOf(1, 2)

suspend fun labels(): List<Flow<Int>> = emptyList()
