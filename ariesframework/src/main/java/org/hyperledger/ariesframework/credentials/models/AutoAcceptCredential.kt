package org.hyperledger.ariesframework.credentials.models

enum class AutoAcceptCredential {
    /**
     * Always auto accepts the credential no matter if it changed in subsequent steps.
     */
    Always,

    /**
     * Never auto accept a credential.
     */
    Never,
}
