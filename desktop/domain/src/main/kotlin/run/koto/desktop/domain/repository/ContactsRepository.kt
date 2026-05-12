package run.koto.desktop.domain.repository

import run.koto.desktop.domain.model.Contact

interface ContactsRepository {
    suspend fun list(): Result<List<Contact>>
    suspend fun add(contactId: String, nickname: String = ""): Result<Unit>
    suspend fun remove(contactId: String): Result<Unit>
    suspend fun block(contactId: String): Result<Unit>
    suspend fun unblock(contactId: String): Result<Unit>
}
