package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.RecyclerView
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.json.JSONArray
import org.json.JSONObject

class CredentialListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView(binding.credentialList.credentialList)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val app = application as WalletApp
        val credentials = Anoncreds.proverGetCredentials(app.agent!!.wallet.indyWallet, "{}").get()
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, JSONArray(credentials))
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: CredentialListActivity,
        private val values: JSONArray,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val item = v.tag as JSONObject
            val intent = Intent(v.context, CredentialDetailActivity::class.java).apply {
                putExtra(CredentialDetailFragment.ARG_CREDENTIAL, item.toString())
            }
            v.context.startActivity(intent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.credential_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position] as JSONObject
            holder.contentView.text = item.getString("referent")

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.length()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var contentBinding = CredentialListContentBinding.bind(view)
            val contentView: TextView = contentBinding.content
        }
    }
}
