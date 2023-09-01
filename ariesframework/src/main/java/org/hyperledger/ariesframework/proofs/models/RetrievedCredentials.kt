package org.hyperledger.ariesframework.proofs.models

class RetrievedCredentials {
    var requestedAttributes: MutableMap<String, List<RequestedAttribute>> = mutableMapOf()
    var requestedPredicates: MutableMap<String, List<RequestedPredicate>> = mutableMapOf()
}
