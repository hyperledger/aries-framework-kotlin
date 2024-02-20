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
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding

class CredentialListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
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
        val credentials = runBlocking { app.agent.credentialRepository.getAll() }
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentials)
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: CredentialListActivity,
        private val values: List<CredentialRecord>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val item = v.tag as CredentialRecord
            val intent = Intent(v.context, CredentialDetailActivity::class.java).apply {
                putExtra(CredentialDetailFragment.ARG_CREDENTIAL, item.credential)
                putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, item.credentialId)
            }
            v.context.startActivity(intent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.credential_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.contentView.text = item.credentialId

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var contentBinding = CredentialListContentBinding.bind(view)
            val contentView: TextView = contentBinding.content
        }
    }
}
