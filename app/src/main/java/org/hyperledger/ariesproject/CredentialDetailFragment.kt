package org.hyperledger.ariesproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding
import org.hyperledger.ariesproject.databinding.CredentialDetailBinding
import anoncreds_uniffi.Credential

class CredentialDetailFragment : Fragment() {

    private var item: Credential? = null
    private var credentialId: String? = null
    private lateinit var detailBinding: ActivityCredentialDetailBinding
    private lateinit var binding: CredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_CREDENTIAL)) {
                item = Credential(it.getString(ARG_CREDENTIAL)!!)
                credentialId = it.getString(ARG_CREDENTIAL_ID)
                detailBinding = ActivityCredentialDetailBinding.inflate(layoutInflater)
                detailBinding.toolbarLayout.title = getString(R.string.title_credential_detail)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = CredentialDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        item?.let {
            val attrs = it.values()
            binding.credentialDetail.text = attrs.map { attr ->
                "${attr.key}: ${attr.value}"
            }.joinToString("\n")

            val activity = activity as CredentialDetailActivity
            val app = activity.application as WalletApp

            // Bind the delete button to the delete action
            binding.deleteCredentialButton.setOnClickListener {
                // To prevent multiple clicks
                binding.deleteCredentialButton.isEnabled = false
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.title_delete_cred)
                    .setMessage(R.string.title_delete_cred_detail)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            app.agent.credentialRepository.deleteById(credentialId!!)
                            activity.runOnUiThread {
                                dialog.dismiss()
                                activity.finish()
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel) { dialog, which ->
                        binding.deleteCredentialButton.isEnabled = true
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
        }
        return rootView
    }

    companion object {
        const val ARG_CREDENTIAL = "item_credential"
        const val ARG_CREDENTIAL_ID = "item_credential_id"
    }
}
