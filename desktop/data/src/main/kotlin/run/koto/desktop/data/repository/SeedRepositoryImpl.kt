package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.SeedStore
import run.koto.desktop.domain.repository.SeedRepository

class SeedRepositoryImpl(
    private val store: SeedStore,
) : SeedRepository {
    override suspend fun read(): List<String>? = withContext(Dispatchers.IO) { store.read() }
}
