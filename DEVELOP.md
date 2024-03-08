# Framework Development Guide

## Framework Internals

Aries Framework Kotlin refers to [Aries Framework Swift](https://github.com/hyperledger/aries-framework-swift) a lot, so the structure is similar to it.

Take a look at the diagrams in the [doc](https://github.com/hyperledger/aries-framework-swift/blob/main/doc/dev_general.md) to understand the basics of Aries and the framework.

### Agent

Agent is the main class of Aries Framework Kotlin. Mobile apps will use this class to create a connection, receive a credential, and so on. It helps mobile apps to become Aries agents. Agent class holds all the commands, services, repositories, and wallet instances. Agent also has a message sender and a receiver along with a dispatcher. Dispatcher dispatches messages to the corresponding `MessageHandler` by its type and sends the outbound messages back when the message handlers return them.

Aries Framework Kotlin only provides outbound transports, not inbound transports. It provides `HttpOutboundTransport` and `WsOutboundTransport` for HTTP and WebSocket, respectively. Agent selects the outbound transport automatically by the endpoint of the counterparty agent. HTTP outbound transport can be used when the agent uses a mediator. WebSocket outbound transport can be used with or without a mediator. `SubjectOutboundTransport` is for testing.

### Repository and Records

Repository classes provide APIs to store and retrieve records. The operation is done using Indy SDK which uses sqlite as a storage. Records can have custom tags and can be searched by the tags.

### JSON encoding and decoding

Aries Framework Kotlin uses `kotlinx.serialization` for JSON encoding and decoding. `AgentMessage` types, `BaseRecord` types, and model types used in these types should be annotated with `@Serializable`.

Serializing `AgentMessage` is a little bit tricky because `kotlinx.serialization` use `type` property as a discriminator for polymorphic serialization. But, we need the `type` property should not be touched by the serializer.
So, we use [Content base serializer](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-content-polymorphic-serializer) for decoding and specify the serializer explicitly for encoding not to use the default polymorphic serializer. This requires us to use our custom `MessageSerializer` for encoding and decoding `AgentMessage`.

`kotlinx.serialization` does not allow unknown keys in JSON by default. We need to use `ignoreUnknownKeys = true` in the `Json {}` builder to allow unknown keys when decoding JSON.

### Connection

Aries agents make connections to exchange encrypted messages with each other. The connection here does not refer to the actual internet connection, but an information about the agents involved in the communication. `ConnectionRecord` is created through [Connection Protocol](https://github.com/hyperledger/aries-rfcs/tree/main/features/0160-connection-protocol) and contains data such as did, keys, endpoints, label, etc.

Aries agents find the existing connection by the keys in the message, so each connection must be created with a unique key. Agents know where to send messages by the endpoint stored in the connection records. Mobile agents using Aries Framework Kotlin do not have reachable endpoints, then how the counterpart agents can send messages to us? There are 2 solutions for this.
1. Using mediator. Agents use mediator's endpoints as their endpoints when creating a connection, then the messages for the agents will be sent to the mediator and agents can fetch the messages from the mediator later.
2. Using WebSocket and keep the WebSocket connection open. Agents using Aries Framework Javascript use the WebSocket session to send messages if the session is open, and they do not close the session when `return-route` is specified. This solution has limitations because the communication is possible only when the WebSocket session is open, and it's not guaranteed that other Aries Frameworks would not close the session. But, this could be a convenient solution depending on the situation.

### MediatoinRecipient

`AgentConfig.mediatorConnectionsInvite` is a connection invitation url from a mediator. An agent connects to the mediator when it is initialized. After the connection is made, it starts mediation protocol with the mediator. The mediation protocol is performed only once if successful, and the result is saved as an `MediationRecord`. `Agent.isInitialized()` becomes `true` when this process is done. Agents pick up messages from the mediator periodically.

Aries Framework Kotlin supports only one mediator. If the `AgentConfig.mediatorConnectionsInvite` is changed, agent will remove the existing `MediationRecord` and do the mediation protocol again.

### Transport Return Route

Agents can specify `return-route` for messages using the [transport decorator](https://github.com/hyperledger/aries-rfcs/tree/main/features/0092-transport-return-route). Aries Framework Kotlin is specifing `return-route` as `all` for all outbound messages in the `MessageSender` class.

## Testing

### Running unit tests

Run the unit tests from the terminal of Android Studio as follows:

```bash
./gradlew :ariesframework:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest
```

As this will run android tests on the connected device, you need to run the emulator before running the tests. Credentials tests, proofs tests, and agent tests are annotated with `@LargeTest` to filter them out.

### Credentials and proofs tests

These tests use `TestHelper.prepareForIssuance()` to register a schema and a credential definition on the ledger.
`Anoncreds.issuerCreateAndStoreCredentialDef()` function of Indy SDK is used there and hangs very often, so the tests will fail most of the time. You can try to run the tests multiple times to see if they pass when the function does not hang.

`TestHelper` uses bcovrin testnet. Local indy pool cannot be used because the IP `127.0.0.1` is interpreted differently in the emulator and the indy pool.

### AgentTest preparation

`AgentTest` requires a mediator and another agent to offer credentials. We use Aries Framework Javascript for this purpose.

Clone the forked Aries Framework Javascript repository.
```bash
git clone https://github.com/conanoc/aries-framework-javascript.git
cd aries-framework-javascript
git checkout demo_kotlin
```

Build and run the mediator. It requires nodejs, yarn and npx.
Agents will use IP `10.0.2.2` instead of `127.0.0.1` to connect to the emulator.
```bash
$ cd aries-framework-javascript
$ yarn install
$ cd samples
$ sudo ifconfig lo0 10.0.2.2 alias
$ AGENT_ENDPOINTS=http://10.0.2.2:3001 npx ts-node mediator.ts
```

`testDemoFaber()` tests the credential exchange flow.
Run the faber agent in demo directory.
```bash
$ cd aries-framework-javascript/demo
$ yarn install
$ yarn faber
```

Then, get the invitation urls from faber agent.
Run `testDemoFaber()` with this url and operate the faber agent to issue a credential.
Aliasing `10.0.2.2` as `lo0` is needed to allow the local mediator and the local faber can communicate with each other with the IP `10.0.2.2`.

### Testing using the sample app

You can run the sample app in `/app` directory. This sample app uses [Indicio Public Mediator](https://indicio-tech.github.io/mediator/) and connects to other agents by receiving invitions by scanning QR codes or by entering invitation urls. You can use the sample app to test the credential exchange flow and the proof exchange flow.
