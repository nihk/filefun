package nick.filefun

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nick.filefun.databinding.DocumentItemBinding
import nick.filefun.databinding.SafFragmentBinding
import java.io.BufferedReader
import kotlin.coroutines.CoroutineContext

// todo: how to create directory programmatically (without SAF), once persistable URI from SAF is taken?
class SafFragment : Fragment(R.layout.saf_fragment) {
    lateinit var binding: SafFragmentBinding
    lateinit var viewModel: SafViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = SafFragmentBinding.bind(view)
        val factory = SafViewModel.Factory(view.context.applicationContext, view.context.prefs())
        viewModel = ViewModelProvider(this, factory).get(SafViewModel::class.java)

        setUpDocumentSaveAs()
        setUpSingleDocumentOpeningAndManagement()
        setUpDocumentTreeUx()
    }

    fun setUpDocumentSaveAs() {
        val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val document = Document(
                uri = uri,
                name = binding.fileNameInput.text.toString(),
                content = binding.fileContent.text.toString()
            )
            viewModel.saveDocument(document)
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
    }

    fun setUpSingleDocumentOpeningAndManagement() {
        val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            viewModel.openDocument(uri ?: return@registerForActivityResult)
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

        viewModel.singleDocument()
            .onEach { document ->
                binding.fileNameInput.setText(document.name)
                binding.fileContent.setText(document.content)
                if (document != emptyDocument) {
                    Log.d("asdf", "Opened document: $document")
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    fun setUpDocumentTreeUx() {
        val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            viewModel.openDirectory(uri ?: return@registerForActivityResult)
        }

        binding.openDirectory.setOnClickListener {
            val startAtRoot = null
            openDocumentTreeLauncher.launch(startAtRoot)
        }

        val adapter = DocumentAdapter()
        binding.documents.adapter = adapter

        viewModel.documents()
            .onEach { documents -> adapter.submitList(documents) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

data class Document(
    val uri: Uri,
    val name: String,
    val content: String
)

val emptyDocument = Document(Uri.EMPTY, "", "")

fun Context.prefs(): SharedPreferences = getSharedPreferences("SafPrefs", Context.MODE_PRIVATE)

@Suppress("BlockingMethodInNonBlockingContext")
@SuppressLint("StaticFieldLeak")
class SafViewModel(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val ioContext: CoroutineContext
) : ViewModel() {

    private val singleDocument = MutableStateFlow(emptyDocument)
    fun singleDocument(): StateFlow<Document> = singleDocument

    private val documents = MutableStateFlow<List<Document>>(emptyList())
    fun documents(): StateFlow<List<Document>> = documents

    private var openedDirectory: Job? = null

    init {
        prefs.directoryUri?.let { uri ->
            openDirectory(uri)
        }
    }

    private var SharedPreferences.directoryUri: Uri?
        get() = getString("uri", null)?.toUri()
        set(value) {
            edit().putString("uri", value?.toString()).apply()
        }

    fun openDocument(uri: Uri) {
        viewModelScope.launch(ioContext) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                singleDocument.value = Document(
                    uri = uri,
                    name = DocumentFile.fromSingleUri(context, uri)?.name!!,
                    content = inputStream.bufferedReader().use(BufferedReader::readText)
                )
            }
        }
    }

    fun saveDocument(document: Document) {
        viewModelScope.launch(ioContext) {
            context.contentResolver.apply {
                openOutputStream(document.uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { it.write(document.content) }
                }
                takeAllUriPermissions(document.uri) // Not strictly necessary for this current use case
            }
            singleDocument.value = document
        }
    }

    fun delete() {
        viewModelScope.launch(ioContext) {
            DocumentFile.fromSingleUri(context, singleDocument.value.uri)?.delete()
            singleDocument.value = emptyDocument
        }
    }

    fun update(content: String) {
        saveDocument(singleDocument.value.copy(content = content))
    }

    fun ContentResolver.takeAllUriPermissions(uri: Uri) {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun openDirectory(uri: Uri) {
        Log.d("asdf", "Opening directory: $uri")
        openedDirectory?.cancel()
        openedDirectory = watchDirectory(uri)
            .onStart { prefs.directoryUri = uri }
            .onStart { context.contentResolver.takeAllUriPermissions(uri) }
            .onStart { emit(queryDirectory(uri)) }
            .onEach { documents.value = it }
            .flowOn(ioContext)
            .launchIn(viewModelScope)
    }

    private fun watchDirectory(uri: Uri) = callbackFlow<List<Document>> {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                offer(queryDirectory(uri))
            }
        }

        // fixme: the provider backing this tree URI isn't notifying any observers of changes
        context.contentResolver.registerContentObserver(uri, true, observer)

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun queryDirectory(uri: Uri): List<Document> {
        val directory = DocumentFile.fromTreeUri(context, uri) ?: error("This should never happen")
        return directory.listFiles().map { documentFile: DocumentFile ->
            Document(
                uri = documentFile.uri,
                name = documentFile.name ?: "Unknown",
                content = documentFile.type ?: "Unknown"
            )
        }
    }

    class Factory(
        private val context: Context,
        private val prefs: SharedPreferences,
        private val ioContext: CoroutineContext = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SafViewModel(context.applicationContext, prefs, ioContext) as T
        }
    }
}

class DocumentViewHolder(
    private val binding: DocumentItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(document: Document) {
        binding.name.text = document.name
        binding.content.text = document.content
    }
}

object DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {
    override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem.uri == newItem.uri
    }

    override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem == newItem
    }
}

class DocumentAdapter : ListAdapter<Document, DocumentViewHolder>(DocumentDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        return LayoutInflater.from(parent.context)
            .let { inflater -> DocumentItemBinding.inflate(inflater, parent, false) }
            .let { binding -> DocumentViewHolder(binding) }
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}