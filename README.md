# Aries Framework Kotlin

Aries Framework Kotlin is an Android framework for [Aries](https://github.com/hyperledger/aries) protocol.

## Features

Aries Framework Kotlin supports most of [AIP 1.0](https://github.com/hyperledger/aries-rfcs/tree/main/concepts/0302-aries-interop-profile#aries-interop-profile-version-10) features for mobile agents.

### Supported features
- ✅ ([RFC 0160](https://github.com/hyperledger/aries-rfcs/blob/master/features/0160-connection-protocol/README.md)) Connection Protocol
- ✅ ([RFC 0211](https://github.com/hyperledger/aries-rfcs/blob/master/features/0211-route-coordination/README.md)) Mediator Coordination Protocol
- ✅ ([RFC 0095](https://github.com/hyperledger/aries-rfcs/blob/master/features/0095-basic-message/README.md)) Basic Message Protocol
- ✅ ([RFC 0036](https://github.com/hyperledger/aries-rfcs/blob/master/features/0036-issue-credential/README.md)) Issue Credential Protocol
- ✅ ([RFC 0037](https://github.com/hyperledger/aries-rfcs/tree/master/features/0037-present-proof/README.md)) Present Proof Protocol
  - Does not implement alternate begining (Prover begins with proposal)
- ✅ HTTP & WebSocket Transport
- ✅ ([RFC 0434](https://github.com/hyperledger/aries-rfcs/blob/main/features/0434-outofband/README.md)) Out of Band Protocol (AIP 2.0)

### Not supported yet
- ❌ ([RFC 0023](https://github.com/hyperledger/aries-rfcs/tree/main/features/0023-did-exchange)) DID Exchange Protocol (AIP 2.0)
- ❌ ([RFC 0035](https://github.com/hyperledger/aries-rfcs/blob/main/features/0035-report-problem/README.md)) Report Problem Protocol
- ❌ ([RFC 0056](https://github.com/hyperledger/aries-rfcs/blob/main/features/0056-service-decorator/README.md)) Service Decorator

## Requirements & Installation

Aries Framework Kotlin requires Android 7.0+. It is not available on Maven Central yet.

Clone this repo and add a dependency to your app's build.gradle file:
```groovy
dependencies {
    implementation project('path_to_ariesframework')
}
```

You will also need these to use Indy-SDK APIs:
```groovy
dependencies {
    implementation ('org.hyperledger:indy:1.16.0') {
        exclude group: 'net.java.dev.jna', module: 'jna'
    }
    implementation 'net.java.dev.jna:jna:5.8.0@aar'
}
```

You need additional dependencies if you include the framework as an AAR file:
```groovy
dependencies {
    implementation files('path_to_aar_file')
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0"
    implementation 'org.slf4j:slf4j-api:1.7.32'
    implementation 'ch.qos.logback:logback-classic:1.2.6'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-datetime:0.4.0'
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
}
```

## Usage

App development using Aries Framework Kotlin is done in following steps:
1. Create an Agent instance.
2. Create a connection with another agent by receiving a connection invitation.
3. Receive credentials or proof requests by subscribing to event bus.

### Create an Agent instance

```kotlin
    val config = AgentConfig(
        walletKey = key,
        genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
        mediatorConnectionsInvite = invitationUrl,
        mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
        label = "SampleApp",
        autoAcceptCredential = AutoAcceptCredential.Never,
        autoAcceptProof = AutoAcceptProof.Never,
    )
    val agent = Agent(applicationContext, config)
    agent.initialize()
```

To create an agent, first create a key to encrypt the wallet and save it in the [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).
```Kotlin
    val key = Wallet.generateKey()
```

A genesis file for the indy pool should be included as a resource in the app bundle and should be copyed to the file system before initializing the agent.
```kotlin
    val genesisPath = "genesis.txn"
    val inputStream = applicationContext.assets.open(genesisPath)
    val file = File(applicationContext.filesDir.absolutePath, genesisPath)
    if (!file.exists()) {
        file.outputStream().use { inputStream.copyTo(it) }
    }
```

If you want to use a mediator, set the `mediatorConnectionsInvite` in the config.
`mediatorConnectionsInvite` is a url containing either a connection invitation or an out-of-band invitation.
`mediatorPickupStrategy` need to be `MediatorPickupStrategy.Implicit` to connect to an ACA-Py mediator.

You can use WebSocket transport without a mediator, but you will need a mediator if the counterparty agent only supports http transport.

### Receive an invitation

Create a connection by receiving a connection invitation.
```kotlin
    val (_, connection) = agent.oob.receiveInvitationFromUrl(url)
```

You will generally get the invitation url by QR code scanning.
Once the connection is created, it is stored in the wallet and your counterparty agent can send you a credential or a proof request using the connection at any time. The connection record contains keys to encrypt or decrypt messages exchanged through the connection.

### Receive credentials or proof requests

Subscribe to agent.eventBus to receive events from the agent and use `agent.credentials` or `agent.proofs` commands to handle the requests.

```kotlin
    private fun subscribeEvents() {
        val app = application as WalletApp
        app.agent!!.eventBus.subscribe<AgentEvents.CredentialEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == CredentialState.OfferReceived) {
                    getCredential(it.record.id)
                } else if (it.record.state == CredentialState.Done) {
                    showAlert("Credential received")
                }
            }
        }
        app.agent!!.eventBus.subscribe<AgentEvents.ProofEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == ProofState.RequestReceived) {
                    sendProof(it.record.id)
                } else if (it.record.state == ProofState.Done) {
                    showAlert("Proof done")
                }
            }
        }
    }

    private fun getCredential(id: String) {
        val app = application as WalletApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent!!.credentials.acceptOffer(
                    AcceptOfferOptions(credentialRecordId = id, autoAcceptCredential = AutoAcceptCredential.Always),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to receive a credential.")
                }
            }
        }
    }

    private fun sendProof(id: String) {
        val app = application as WalletApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retrievedCredentials = app.agent!!.proofs.getRequestedCredentialsForProofRequest(id)
                val requestedCredentials = app.agent!!.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
                app.agent!!.proofs.acceptRequest(id, requestedCredentials)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to present proof.")
                }
            }
        }
    }
```

If you set `autoAcceptCredential` and `autoAcceptProof` to `Always` in the config, it will be done automatically and you don't need to subscribe to the events and handle the requests.

Another way to handle those requests is to implement your own `MessageHandler` class and register it to the agent.
```kotlin
    val messageHandler = MyOfferCredentialHandler()
    agent.dispatcher.registerHandler(messageHandler)
```

For your information, Aries Framework Kotlin refers to [Aries Framework Swift](https://github.com/hyperledger/aries-framework-swift) a lot, so the class name and API are almost the same.

## Sample App

`app` directory contains an Android sample app that demonstrates how to use Aries Framework Kotlin. The app receives a connection invitation from a QR code or from a URL input and handles credential offers and proof requests.

The agent is created in the `WalletApp.kt` file and you can set a mediator connection invitation url there, if you want.

There is a genesis files in the `app/src/main/assets` directory.
- `bcovrin-genesis.txn` is for the [GreenLight Dev Ledger](http://dev.greenlight.bcovrin.vonx.io/)

## Contributing

We welcome contributions to Aries Framework Kotlin. Please see our [Developer Guide](DEVELOP.md) for more information.

## License

Aries Framework Kotlin is licensed under the [Apache License 2.0](LICENSE).
