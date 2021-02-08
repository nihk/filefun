package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nick.filefun.databinding.CreateFileFragmentBinding
import java.io.File
import kotlin.coroutines.CoroutineContext

class CreateFileFragment : Fragment(R.layout.create_file_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = CreateFileFragmentBinding.bind(view)
        val factory = CreateFileViewModel.Factory(view.context.applicationContext)
        val viewModel = ViewModelProvider(this, factory).get(CreateFileViewModel::class.java)

        binding.saveFile.setOnClickListener {
            viewModel.saveTextFile(
                name = binding.fileNameInput.text.toString(),
                text = binding.fileContent.text.toString()
            )
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

@SuppressLint("StaticFieldLeak")
class CreateFileViewModel(
    private val context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : ViewModel() {

    private val filesDir by lazy { context.filesDir }

    fun saveTextFile(name: String, text: String) {
        viewModelScope.launch {
            withContext(ioContext) {
                File(filesDir, "$name.txt").writeText(text)
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CreateFileViewModel(context) as T
        }
    }
}