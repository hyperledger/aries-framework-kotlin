package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.didcommx.peerdid.DIDCommServicePeerDID
import org.didcommx.peerdid.DIDDocPeerDID
import org.didcommx.peerdid.OtherService
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.Ed25119Sig2018
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.PublicKey
import org.hyperledger.ariesframework.util.DIDParser

@Serializable
class DidDoc(
    @SerialName("@context")
    @EncodeDefault
    val context: String = "https://w3id.org/did/v1",
    var id: String,
    var publicKey: List<PublicKey> = emptyList(),
    var authentication: List<Authentication> = emptyList(),
    var service: List<DidDocService> = emptyList(),
) {
    fun publicKey(id: String) = publicKey.find { it.id == id }

    inline fun <reified T>servicesByType() = service.filter { it is T }.map { it as T }

    fun didCommServices() = servicesByType<DidComm>().sortedByDescending { it.priority }

    constructor(didDocument: DIDDocPeerDID) : this(id = didDocument.did) {
        if (didDocument.authentication.isEmpty()) {
            throw Exception("No authentication method found in DIDDocument")
        }
        val recipientKey = didDocument.authentication.first().verMaterial.value.toString()
        publicKey = listOf(
            Ed25119Sig2018(
                id = "$id#1",
                controller = id,
                publicKeyBase58 = recipientKey
            )
        )
        authentication = listOf(
            ReferencedAuthentication(
                type = Ed25119Sig2018.type,
                publicKey = publicKey[0].id
            )
        )
        didDocument.service?.let {services ->
            service = services.map {
                when (it) {
                    is OtherService -> {
                        val routingKeys = it.data["routingKeys"] as List<String>?
                        val parsedRoutingKeys = routingKeys?.map { DIDParser.convertDidKeyToVerkey(it) }
                        DidCommService(
                            id = it.data["id"] as String,
                            serviceEndpoint = it.data["serviceEndpoint"] as String,
                            recipientKeys = it.data["recipientKeys"] as List<String>,
                            routingKeys = parsedRoutingKeys,
                            priority = it.data["priority"] as Int?
                        )
                    }
                    is DIDCommServicePeerDID -> {
                        val parsedRoutingKeys = it.serviceEndpoint.routingKeys.map { DIDParser.convertDidKeyToVerkey(it) }
                        DidCommService(
                            id = it.id,
                            serviceEndpoint = it.serviceEndpoint.uri,
                            recipientKeys = listOf(recipientKey),
                            routingKeys = parsedRoutingKeys,
                        )
                    }
                    else -> throw Exception("Unsupported service type")
                }
            }
        }
    }
}
