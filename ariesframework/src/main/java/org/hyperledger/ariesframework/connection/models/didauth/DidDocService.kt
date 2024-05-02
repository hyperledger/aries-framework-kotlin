package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed class DidDocService {
    abstract val id: String
}

interface DidComm {
    val id: String
    val serviceEndpoint: String
    val recipientKeys: List<String>
    val routingKeys: List<String>?
    val priority: Int?
    val accept: List<String>?
}

val didDocServiceModule = SerializersModule {
    polymorphic(DidDocService::class) {
        subclass(DidCommService::class)
        subclass(DidCommV2Service::class)
        subclass(IndyAgentService::class)
        subclass(DidDocumentService::class)
        defaultDeserializer { DidDocumentService.serializer() }
    }
}
