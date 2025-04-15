package adapters

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import com.example.taller20.databinding.ContactItemBinding

//ContactsAdapter hereda de CursosrAdapter
class ContactsAdapter(context: Context?, c: Cursor?, flags: Int) :
    CursorAdapter(context, c, flags) {

    //Crea una nueva vista para cada fila del ListView
    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        val binding = ContactItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return binding.root
    }

    //Rellena la vista con los datos de la fila actual del Cursor
    override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
        //Vincula objeto view con su correspondiente binding, creada en newView, aca se usa para acceder a los componentes
        val binding = ContactItemBinding.bind(view!!)
        val id = cursor!!.getInt(0)
        val name = cursor.getString(1)

        binding.idContact.text = id.toString()
        binding.name.text = name
    }
}
