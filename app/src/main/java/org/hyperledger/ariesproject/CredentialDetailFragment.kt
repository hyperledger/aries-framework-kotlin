package org.hyperledger.ariesproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding
import org.hyperledger.ariesproject.databinding.CredentialDetailBinding
import org.json.JSONObject

class CredentialDetailFragment : Fragment() {

    private var item: JSONObject? = null
    private lateinit var detailBinding: ActivityCredentialDetailBinding
    private lateinit var binding: CredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_CREDENTIAL)) {
                item = JSONObject(it.getString(ARG_CREDENTIAL))
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
            val attrs = it.getJSONObject("attrs")
            val keys = attrs.keys()
            binding.credentialDetail.text = keys.asSequence().map { key ->
                val value = attrs.getString(key)
                "$key: $value"
            }.joinToString("\n")
        }

        return rootView
    }

    companion object {
        const val ARG_CREDENTIAL = "item_credential"
    }
}
