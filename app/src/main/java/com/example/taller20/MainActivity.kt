package com.example.taller20

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taller20.CameraActivity
import com.example.taller20.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactsButton.setOnClickListener{
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        binding.cameraButton.setOnClickListener{
            startActivity(Intent(this, CameraActivity::class.java))
        }
        binding.osmapButton.setOnClickListener{
            startActivity(Intent(this, GoogleMapsActivity::class.java))
        }

    }
}