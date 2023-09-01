package org.hyperledger.ariesframework.connection.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.storage.BaseRecord
import java.lang.Exception

@Serializable
data class ConnectionRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var state: ConnectionState,
    var role: ConnectionRole,
    var didDoc: DidDoc,
    var did: String,
    var verkey: String,
    var theirDidDoc: DidDoc? = null,
    var theirDid: String? = null,
    var theirLabel: String? = null,
    var invitation: ConnectionInvitationMessage? = null,
    var outOfBandInvitation: OutOfBandInvitation? = null,
    var alias: String? = null,
    var autoAcceptConnection: Boolean? = null,
    var imageUrl: String? = null,
    var multiUseInvitation: Boolean,
    var threadId: String? = null,
    var mediatorId: String? = null,
    var errorMessage: String? = null,
) : BaseRecord() {
    override fun getTags(): Tags {
        var tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["state"] = state.name
        tags["role"] = role.name

        invitation?.recipientKeys?.firstOrNull()?.let {
            tags["invitationKey"] = it
        }
        outOfBandInvitation?.invitationKey()?.let {
            tags["invitationKey"] = it
        }

        threadId?.let { tags["threadId"] = it }
        tags["verkey"] = verkey
        theirKey()?.let { tags["theirKey"] = it }
        mediatorId?.let { tags["mediatorId"] = it }
        tags["did"] = did
        theirDid?.let { tags["theirDid"] = it }

        return tags
    }

    fun myKey(): String? {
        return didDoc.didCommServices().firstOrNull()?.recipientKeys?.firstOrNull()
    }

    fun theirKey(): String? {
        return theirDidDoc?.didCommServices()?.firstOrNull()?.recipientKeys?.firstOrNull()
    }

    fun isReady(): Boolean {
        return state in listOf(ConnectionState.Responded, ConnectionState.Complete)
    }

    fun assertReady() {
        if (!isReady()) {
            throw Exception(
                "Connection record is not ready to be used. Expected ${ConnectionState.Responded} or ${ConnectionState.Complete}," +
                    " found invalid state $state",
            )
        }
    }

    fun assertState(vararg expectedStates: ConnectionState) {
        if (state !in expectedStates) {
            throw Exception("Connection record is in invalid state $state. Valid states are: ${expectedStates.joinToString { it.name }}.")
        }
    }

    fun assertRole(expectedRole: ConnectionRole) {
        if (role != expectedRole) {
            throw Exception("Connection record has invalid role $role. Expected role $expectedRole.")
        }
    }
}
