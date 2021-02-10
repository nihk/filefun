package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
import java.io.BufferedReader
import kotlin.coroutines.CoroutineContext

// Read more at: https://commonsware.com/blog/2019/10/19/scoped-storage-stories-saf-basics.html
// todo: use DocumentFile APIs where appropriate
// todo: how to create directory?
// todo: request access/create an entire directory to use
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
        }

        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            viewModel.openTextDocument(uri ?: return@registerForActivityResult)
        }

        binding.saveAs.setOnClickListener {
            val fileName = binding.fileNameInput.text.toString().run {
                if (!endsWith(".txt")) {
                    "$this.txt"
                } else {
                    this
                }
            }
            createDocumentLauncher.launch(fileName)
        }

        binding.open.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/*"))
        }

        binding.delete.setOnClickListener {
            viewModel.delete()
        }

        binding.update.setOnClickListener {
            viewModel.update(binding.fileContent.text.toString())
        }

        viewModel.textDocuments()
            .onEach { textDocument ->
                binding.fileNameInput.setText(textDocument.name)
                binding.fileContent.setText(textDocument.content)
                Log.d("asdf", "Opened document: $textDocument")
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

val emptyTextDocument = TextDocument(Uri.EMPTY, "", "")

@Suppress("BlockingMethodInNonBlockingContext")
@SuppressLint("StaticFieldLeak")
class SafViewModel(
    private val context: Context,
    private val ioContext: CoroutineContext
) : ViewModel() {

    private val textDocuments = MutableStateFlow(emptyTextDocument)
    fun textDocuments(): Flow<TextDocument> = textDocuments

    fun openTextDocument(uri: Uri) {
        viewModelScope.launch(ioContext) {
            val textDocumentName = context.contentResolver.query(uri, null, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            } ?: error("Couldn't find document name for $uri")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                textDocuments.value = TextDocument(
                    uri = uri,
                    name = textDocumentName,
                    content = inputStream.bufferedReader().use(BufferedReader::readText)
                )
            }
        }
    }

    fun saveTextDocument(textDocument: TextDocument) {
        viewModelScope.launch(ioContext) {
            context.contentResolver.openOutputStream(textDocument.uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { it.write(textDocument.content) }
            }
            textDocuments.value = textDocument
        }
    }

    fun delete() {
        viewModelScope.launch(ioContext) {
            DocumentsContract.deleteDocument(
                context.contentResolver,
                textDocuments.value.uri
            )
            textDocuments.value = emptyTextDocument
        }
    }

    fun update(content: String) {
        saveTextDocument(textDocuments.value.copy(content = content))
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