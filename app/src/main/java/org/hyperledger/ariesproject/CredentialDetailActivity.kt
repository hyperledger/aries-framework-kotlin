package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding

class CredentialDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.detailToolbar)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val fragment = CredentialDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(
                        CredentialDetailFragment.ARG_CREDENTIAL,
                        intent.getStringExtra(CredentialDetailFragment.ARG_CREDENTIAL),
                    )
                }
            }

            supportFragmentManager.beginTransaction()
                .add(binding.credentialDetailContainer.id, fragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                navigateUpTo(Intent(this, CredentialListActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
