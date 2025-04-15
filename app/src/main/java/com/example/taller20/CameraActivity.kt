package com.example.taller20

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.taller20.databinding.ActivityCameraBinding
import java.io.File

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    // Ruta del archivo
    private lateinit var uri: Uri

    // Abre galeria y permite seleccionar imagen
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(), ActivityResultCallback {
            // it es el uri?
            loadImage(it!!)
        }
    )

    // Abre camara y toma una foto
    private val cameraLauncher = registerForActivityResult(
        // TakePicture guarda la imagen en el uri
        ActivityResultContracts.TakePicture(),
        ActivityResultCallback {
            if(it){
                loadImage(uri)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.GalleryButton.setOnClickListener{
            // Lanza selector de iamgenes
            galleryLauncher.launch("image/*")
        }
        binding.CameraButton.setOnClickListener{
            // Crea archivo pocFromCamera dentro de almacenamiento interno
            val file = File(getFilesDir(),"picFromCamera")
            // Genera uri para acceder al archivo
            uri = FileProvider.getUriForFile(baseContext,baseContext.packageName+".fileprovider",file)
            // Lanza la camara
            cameraLauncher.launch(uri)
        }
        binding.ButtonDevolver.setOnClickListener{
            //regresa a MainActivity
            finish()
        }
    }

    private fun loadImage(uri: Uri) {
        // Abre iamgen desde la ruta
        val imageStream = getContentResolver().openInputStream(uri)
        // Convierte los bytes de la imagen en un objeto que Android puede mostrar
        val bitmap = BitmapFactory.decodeStream(imageStream)
        binding.Image.setImageBitmap(bitmap)

    }
}