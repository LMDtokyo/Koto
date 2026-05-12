package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.data.remote.dto.ContactDto
import run.koto.desktop.domain.model.Contact
import run.koto.desktop.domain.repository.ContactsRepository

class ContactsRepositoryImpl(
    private val userApi: UserApi,
) : ContactsRepository {

    private val log = LoggerFactory.getLogger(ContactsRepositoryImpl::class.java)

    override suspend fun list(): Result<List<Contact>> = runCatching {
        withContext(Dispatchers.IO) { userApi.listContacts() }.map { it.toDomain() }
    }.onFailure { log.warn("list contacts failed", it) }

    override suspend fun add(contactId: String, nickname: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { userApi.addContact(contactId, nickname) }
    }.onFailure { log.warn("add contact failed id={}", contactId, it) }

    override suspend fun remove(contactId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { userApi.removeContact(contactId) }
    }.onFailure { log.warn("remove contact failed id={}", contactId, it) }

    override suspend fun block(contactId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { userApi.blockContact(contactId) }
    }.onFailure { log.warn("block contact failed id={}", contactId, it) }

    override suspend fun unblock(contactId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { userApi.unblockContact(contactId) }
    }.onFailure { log.warn("unblock contact failed id={}", contactId, it) }
}

private fun ContactDto.toDomain() = Contact(
    contactId = contact_id,
    nickname  = nickname,
    addedAt   = added_at,
    blocked   = blocked,
)
