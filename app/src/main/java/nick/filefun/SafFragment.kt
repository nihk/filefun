package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nick.filefun.databinding.SafFragmentBinding
import kotlin.coroutines.CoroutineContext

// Read more at: https://commonsware.com/blog/2019/10/19/scoped-storage-stories-saf-basics.html
class SafFragment : Fragment(R.layout.saf_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = SafFragmentBinding.bind(view)
        val factory = SafViewModel.Factory(view.context.applicationContext)
        val viewModel = ViewModelProvider(this, factory).get(SafViewModel::class.java)

        val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val textDocument = TextDocument(
                uri = uri,
                name = binding.fileNameInput.text.toString(),
                content = binding.fileContent.text.toString()
            )
            viewModel.saveTextDocument(textDocument)
            Log.d("asdf", "Saving document to: $uri")
        }

        binding.saveAs.setOnClickListener {
            createDocumentLauncher.launch("${binding.fileNameInput.text}.txt")
        }

        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            viewModel.openTextDocument(uri ?: return@registerForActivityResult)
            Log.d("asdf", "Opening document: $uri")
        }

        binding.open.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/*"))
        }

        viewModel.textDocuments()
            .onEach { textDocument ->
                binding.fileNameInput.setText(textDocument.name)
                binding.fileContent.setText(textDocument.content)
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

data class TextDocument(
    val uri: Uri,
    val name: String,
    val content: String
)

@SuppressLint("StaticFieldLeak")
class SafViewModel(
    private val context: Context,
    private val ioContext: CoroutineContext
) : ViewModel() {

    private val textDocuments = MutableStateFlow<TextDocument?>(null)
    fun textDocuments(): Flow<TextDocument> = textDocuments.filterNotNull()

    fun openTextDocument(uri: Uri) {
        viewModelScope.launch(ioContext) {
            @Suppress("BlockingMethodInNonBlockingContext")
            context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->

            }
        }
    }

    fun saveTextDocument(textDocument: TextDocument) {
        viewModelScope.launch(ioContext) {

        }
    }

    class Factory(
        private val context: Context,
        private val ioContext: CoroutineContext = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SafViewModel(context.applicationContext, ioContext) as T
        }
    }
}