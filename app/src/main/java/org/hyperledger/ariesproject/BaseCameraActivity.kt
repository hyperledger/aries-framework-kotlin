package org.hyperledger.ariesproject

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.hyperledger.ariesproject.databinding.ActivityQrcodeBinding

abstract class BaseCameraActivity : AppCompatActivity() {

    lateinit var binding: ActivityQrcodeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cameraView.setLifecycleOwner(this)
    }
}
