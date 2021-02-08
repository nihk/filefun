package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import nick.filefun.databinding.CreateFileFragmentBinding
import java.io.File

class CreateFileFragment : Fragment(R.layout.create_file_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = CreateFileFragmentBinding.bind(view)
        val factory = CreateFileViewModel.Factory(view.context.applicationContext)
        val viewModel = ViewModelProvider(this, factory).get(CreateFileViewModel::class.java)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

@SuppressLint("StaticFieldLeak")
class CreateFileViewModel(
    private val context: Context
) : ViewModel() {

    fun saveTextDocument(name: String, text: String) {
        val file = File(context.filesDir, name)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CreateFileViewModel(context) as T
        }

    }
}