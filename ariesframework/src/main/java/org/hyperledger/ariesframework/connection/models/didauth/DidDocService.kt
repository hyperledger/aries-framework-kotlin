package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface DidDocService {
    val id: String
    val serviceEndpoint: String
}

interface DidComm : DidDocService {
    val recipientKeys: List<String>
    val routingKeys: List<String>?
    val priority: Int?
    val accept: List<String>?
}

val didDocServiceModule = SerializersModule {
    polymorphic(DidDocService::class) {
        subclass(DidCommService::class)
        subclass(IndyAgentService::class)
        subclass(DidDocumentService::class)
        defaultDeserializer { DidDocumentService.serializer() }
    }
}
