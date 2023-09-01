package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.PublicKey

@Serializable
class DidDoc(
    @SerialName("@context")
    @EncodeDefault
    val context: String = "https://w3id.org/did/v1",
    val id: String,
    val publicKey: List<PublicKey>,
    val authentication: List<Authentication>,
    val service: List<DidDocService>,
) {
    fun publicKey(id: String) = publicKey.find { it.id == id }

    inline fun <reified T>servicesByType() = service.filter { it is T }.map { it as T }

    fun didCommServices() = servicesByType<DidComm>().sortedByDescending { it.priority }
}
