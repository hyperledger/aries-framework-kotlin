package org.hyperledger.ariesproject

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.MediationProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessage
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesproject.databinding.ActivityWalletMainBinding
import org.hyperledger.ariesproject.databinding.MenuItemListContentBinding
import org.hyperledger.ariesproject.menu.MainMenu

class WalletMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletMainBinding
    private var credentialProgress: ProgressDialog? = null
    private var proofProgress: ProgressDialog? = null
    private val TAG = "WalletMainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWalletMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        setupRecyclerView(binding.menuItemList.itemList)
        waitForAgentInitialze()

        binding.invitation.setOnEditorActionListener { _, _, _ ->
            val invitation = binding.invitation.text.toString()
            if (invitation.isNotEmpty()) {
                val app = application as WalletApp
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val (_, connection) = app.agent.oob.receiveInvitationFromUrl(invitation)
                        showAlert("Connected to ${connection?.theirLabel ?: "unknown agent"}")
                    } catch (e: Exception) {
                        showAlert("Unable to connect, please check your network connection")
                    }
                }
            }
            true
        }
    }

    override fun onStart() {
        super.onStart()
    }

    private fun subscribeEvents() {
        val app = application as WalletApp
        app.agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == CredentialState.OfferReceived) {
                    runOnConfirm("Accept credential?", action = {
                        getCredential(it.record.id)
                    }, negAction = {
                        declineCredential(it.record.id)
                    })
                } else if (it.record.state == CredentialState.Done) {
                    credentialProgress?.dismiss()
                    showAlert("Credential received")
                }
            }
        }
        app.agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == ProofState.RequestReceived) {
                    runOnConfirm("Accept proof request?", action = {
                        sendProof(it.record.id)
                    }, negAction = {
                        declineProof(it.record.id)
                    })
                } else if (it.record.state == ProofState.Done) {
                    proofProgress?.dismiss()
                    showAlert("Proof done")
                }
            }
        }
        // Show an alert on basic message, this is useful for debugging.
        app.agent.eventBus.subscribe<AgentEvents.BasicMessageEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                showAlert("${it.message}")
            }
        }

        // Show an alert on credential problem report
        app.agent.eventBus.subscribe<AgentEvents.ProblemReportEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                // Check if message type is a CredentialProblemReport
                if (it.message is CredentialProblemReportMessage) {
                    showAlert("Issuer reported a problem while issuing the credential - ${it.message.description.en}")
                }
                if (it.message is PresentationProblemReportMessage) {
                    showAlert("Verifier reported a problem while verifying the presentation - ${it.message.description.en}")
                }
                if (it.message is MediationProblemReportMessage) {
                    showAlert("Mediator reported a problem - ${it.message.description.en}")
                }
            }
        }
    }

    private fun showAlert(message: String) {
        val builder = AlertDialog.Builder(this@WalletMainActivity)
        builder.setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
        builder.create().show()
    }

    private fun runOnConfirm(message: String, action: () -> Unit, negAction: () -> Unit) {
        val builder = AlertDialog.Builder(this@WalletMainActivity)
        builder.setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                action()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                negAction()
            }
        builder.create().show()
    }

    private fun runOnConfirm(message: String, action: () -> Unit) {
        runOnConfirm(message, action) {}
    }

    private fun waitForAgentInitialze() {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Initializing agent")
        progress.setCancelable(false)
        progress.show()

        val timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (app.walletOpened) {
                    subscribeEvents()
                    // This causes a crash:
                    // java.lang.IllegalArgumentException: View=DecorView@39d4aa3[Initializing agent] not attached to window manager
                    // for now we just catch the exception
                    try {
                        progress.dismiss()
                    } catch (e: Exception) {
                        Log.d(TAG, e.message ?: "Unknown error")
                    }
                    cancel()
                }
            }

            override fun onFinish() {
                progress.dismiss()
                showAlert("Failed to open a wallet.")
            }
        }
        timer.start()
    }

    private fun declineCredential(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentials.declineOffer(
                    AcceptOfferOptions(
                        credentialRecordId = id,
                        autoAcceptCredential = AutoAcceptCredential.Never,
                    ),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decline a credential.")
                }
            }
        }
    }

    private fun declineProof(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.proofs.declineRequest(id)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decline a proof.")
                }
            }
        }
    }

    private fun getCredential(id: String) {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Loading")
        progress.setCancelable(true)

        val job = lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentials.acceptOffer(
                    AcceptOfferOptions(credentialRecordId = id, autoAcceptCredential = AutoAcceptCredential.Always),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    progress.dismiss()
                    showAlert("Failed to receive a credential.")
                }
            }
        }

        progress.setOnCancelListener {
            job.cancel()
        }
        progress.show()
        credentialProgress = progress
    }

    private fun sendProof(id: String) {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Sending proof")
        progress.setCancelable(true)

        val job = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retrievedCredentials = app.agent.proofs.getRequestedCredentialsForProofRequest(id)
                val requestedCredentials = app.agent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
                app.agent.proofs.acceptRequest(id, requestedCredentials)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    progress.dismiss()
                    showAlert("Failed to present proof.")
                }
            }
        }

        progress.setOnCancelListener {
            job.cancel()
        }
        progress.show()
        proofProgress = progress
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val app = application as WalletApp
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val qrcodeData = data!!.getStringExtra("qrcode")
                    Log.d("demo", "Scanned code: $qrcodeData")
                    val (_, connection) = app.agent.oob.receiveInvitationFromUrl(qrcodeData!!)
                    showAlert("Connected to ${connection?.theirLabel ?: "unknown agent"}")
                } catch (e: Exception) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Unrecognized qrcode")
                }
            }
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, listOf(MainMenu.GET, MainMenu.LIST))
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: WalletMainActivity,
        private val values: List<MainMenu>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.MenuItemHolder>() {

        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val menu = v.tag as MainMenu
            when (menu) {
                MainMenu.GET -> {
                    val intent = Intent(v.context, BarcodeScannerActivity::class.java)
                    (v.context as Activity).startActivityForResult(intent, 0)
                }

                MainMenu.LIST -> {
                    val intent = Intent(v.context, CredentialListActivity::class.java)
                    v.context.startActivity(intent)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemHolder {
            val binding = MenuItemListContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MenuItemHolder(binding)
        }

        override fun onBindViewHolder(holder: MenuItemHolder, position: Int) {
            val item = values[position]
            holder.contentView.text = item.text

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class MenuItemHolder(val binding: MenuItemListContentBinding) : RecyclerView.ViewHolder(binding.root) {
            val contentView: TextView = binding.content
        }
    }
}
