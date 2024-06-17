package org.hyperledger.ariesframework.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.didcommx.peerdid.DIDDocPeerDID
import org.didcommx.peerdid.VerificationMaterialAgreement
import org.didcommx.peerdid.VerificationMaterialAuthentication
import org.didcommx.peerdid.VerificationMaterialFormatPeerDID
import org.didcommx.peerdid.VerificationMethodTypeAgreement
import org.didcommx.peerdid.VerificationMethodTypeAuthentication
import org.didcommx.peerdid.createPeerDIDNumalgo2
import org.didcommx.peerdid.resolvePeerDID
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.connection.models.didauth.DidCommV2Service
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.models.didauth.DidDocService
import org.hyperledger.ariesframework.connection.models.didauth.ServiceEndpoint
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory

class PeerDIDService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(PeerDIDService::class.java)
    private val encoder = Json { serializersModule = didDocServiceModule }

    /**
     * Create a Peer DID with numAlgo 2 using the provided verkey.
     * This function adds a service of type "did-communication" to the Peer DID if useLegacyService is true,
     * else it adds a service of type "DIDCommMessaging".
     *
     * @param verkey The verkey to use for the Peer DID.
     * @param useLegacyService whether to use the legacy service type or not. Default is true.
     * @return The created Peer DID.
     */
    suspend fun createPeerDID(verkey: String, useLegacyService: Boolean = true): String {
        logger.debug("Creating Peer DID for verkey: $verkey")
        val (endpoints, routingKeys) = agent.mediationRecipient.getRoutingInfo()
        val didRoutingKeys = routingKeys.map { rawKey ->
            val key = DIDParser.convertVerkeyToDidKey(rawKey)
            return@map "$key#${DIDParser.getMethodId(key)}"
        }
        val authKey = VerificationMaterialAuthentication(
            type = VerificationMethodTypeAuthentication.ED25519_VERIFICATION_KEY_2020,
            format = VerificationMaterialFormatPeerDID.BASE58,
            value = verkey,
        )
        val agreementKey = VerificationMaterialAgreement(
            type = VerificationMethodTypeAgreement.X25519_KEY_AGREEMENT_KEY_2019,
            format = VerificationMaterialFormatPeerDID.BASE58,
            value = verkey,
        )
        val service = if (useLegacyService) {
            val service = DidCommService(
                id = "#service-1",
                serviceEndpoint = endpoints.first(),
                routingKeys = didRoutingKeys,
                recipientKeys = listOf("#key-2"),
            )
            encoder.encodeToString<DidDocService>(service)
        } else {
            val service = DidCommV2Service(
                id = "#service-1",
                serviceEndpoint = ServiceEndpoint(
                    uri = endpoints.first(),
                    routingKeys = didRoutingKeys,
                ),
            )
            encoder.encodeToString<DidDocService>(service)
        }
        return createPeerDIDNumalgo2(
            encryptionKeys = listOf(agreementKey),
            signingKeys = listOf(authKey),
            service = service,
        )
    }

    /**
     * Parse a Peer DID into a DidDoc. Only numAlgo 0 and 2 are supported.
     * In case of numAlgo 2 DID, the routing keys should be did:key format.
     *
     * @param did The Peer DID to parse.
     * @return The parsed DID Document.
     */
    suspend fun parsePeerDID(did: String): DidDoc {
        logger.debug("Parsing Peer DID: $did")
        val json = resolvePeerDID(did, VerificationMaterialFormatPeerDID.BASE58)
        logger.debug("Parsed Peer DID JSON: $json")
        val didDocument = DIDDocPeerDID.fromJson(json)
        return DidDoc(didDocument)
    }
}
