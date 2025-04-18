package com.example.taller20

import adapters.ContactsAdapter
import android.R
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.taller20.databinding.ActivityContactsBinding

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    //columnas que quiero leer de los contactos
    val projection = arrayOf(ContactsContract.Profile._ID, ContactsContract.Profile.DISPLAY_NAME)
    lateinit var adapter : ContactsAdapter

    //Registro para pedir permiso, cuando usuario responda se llama a updateUI
    val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            updateUI(it)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialiaclización adapter con un cursor vacío
        adapter = ContactsAdapter(this, null, 0)
        binding.listContacts.adapter = adapter
        // Solicitud del permiso
        contactsPermissionRequest(android.Manifest.permission.READ_CONTACTS)

        binding.ButtonDevolver.setOnClickListener{
            //regresa a MainActivity
            finish()
        }

    }

    private fun contactsPermissionRequest(permission : String){
        // Si el permiso está negado
        if(ContextCompat.checkSelfPermission(this,permission)== PackageManager.PERMISSION_DENIED) {
            // Si ya se negó antes, mostrar explicación
            if (shouldShowRequestPermissionRationale(permission)) {
                Toast.makeText(this, "Necesitamos acceder a tus contactos para mostrarte la lista de los mismos.", Toast.LENGTH_LONG).show()
            }
            // Solicitu de permiso al usuario
            contactsPermission.launch(permission)
        }
        else{
            // Si el permiso está concedido
            updateUI(true)
        }
    }

    fun updateUI(flag: Boolean) {
        if(flag){
            // Leer los contactos si el permiso fue concedido
            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,null,null
            )
            adapter.changeCursor(cursor)

        }else{
            // Mostrar mensaje de error si no hay permiso
            val arreglo = Array<String>(1){i->"Sin acceso"}
            val errorAdapter = ArrayAdapter<String>(this, R.layout.simple_list_item_1,arreglo)
            binding.listContacts.adapter = errorAdapter
        }
    }
}
