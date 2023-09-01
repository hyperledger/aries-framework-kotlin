package org.hyperledger.ariesframework.proofs.models

enum class AutoAcceptProof {
    /**
     * Always auto accepts the proof no matter if it changed in subsequent steps.
     */
    Always,

    /**
     * Never auto accept a proof.
     */
    Never,
}
