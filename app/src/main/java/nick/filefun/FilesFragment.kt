package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.withContext
import nick.filefun.databinding.FilesFragmentBinding
import nick.filefun.databinding.SavedFileItemBinding
import java.io.File
import kotlin.coroutines.CoroutineContext

class FilesFragment : Fragment(R.layout.files_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FilesFragmentBinding.bind(view)
        val factory = FilesViewModel.Factory(FilesDirectory.InternalAppSpecificStorage, view.context.applicationContext)
        val viewModel = ViewModelProvider(this, factory).get(FilesViewModel::class.java)

        binding.saveFile.setOnClickListener {
            viewModel.saveFile(
                name = binding.fileNameInput.text.toString(),
                text = binding.fileContent.text.toString()
            )
        }

        binding.filesGroup.setOnCheckedChangeListener { _, checkedId ->
            val filesDirectory = when (checkedId) {
                binding.filesDir.id -> FilesDirectory.InternalAppSpecificStorage
                binding.cacheDir.id -> FilesDirectory.InternalAppSpecificCache
                binding.externalFilesDir.id -> FilesDirectory.ExternalAppSpecificRoot
                binding.externalCacheDir.id -> FilesDirectory.ExternalAppSpecificCache
                binding.externalFilesDocuments.id -> FilesDirectory.ExternalAppSpecificDocuments
                else -> error("Unknown id: $checkedId")
            }

            viewModel.setFilesDir(filesDirectory)
        }

        val adapter = SavedFilesAdapter(viewModel::deleteFile)
        binding.savedFiles.adapter = adapter

        viewModel.savedFiles()
            .onEach { adapter.submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

sealed class FilesDirectory {
    abstract fun from(context: Context): File?

    object InternalAppSpecificStorage : FilesDirectory() {
        override fun from(context: Context): File? = context.filesDir
    }
    object InternalAppSpecificCache : FilesDirectory() {
        override fun from(context: Context) = context.cacheDir
    }
    object ExternalAppSpecificRoot : FilesDirectory() {
        override fun from(context: Context) = context.getExternalFilesDir(null)
    }
    object ExternalAppSpecificCache : FilesDirectory() {
        override fun from(context: Context) = context.externalCacheDir
    }
    object ExternalAppSpecificDocuments : FilesDirectory() {
        override fun from(context: Context) = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    }
}

@SuppressLint("StaticFieldLeak")
class FilesViewModel(
    initialFilesDirectory: FilesDirectory,
    private val context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : ViewModel() {

    private val savedFiles = MutableStateFlow<List<SavedFile>>(emptyList())
    fun savedFiles(): StateFlow<List<SavedFile>> = savedFiles

    private var filesDir: File = getFilesDir(initialFilesDirectory)

    private var filesChangesJob: Job? = null

    init {
        observeFilesChanges()
    }

    private fun getFilesDir(filesDirectory: FilesDirectory): File {
        return filesDirectory.from(context)!!
    }

    fun setFilesDir(filesDirectory: FilesDirectory) {
        val file = getFilesDir(filesDirectory)
        if (filesDir == file) return

        filesChangesJob?.cancel()
        filesDir = file
        observeFilesChanges()
    }

    private fun observeFilesChanges() {
        filesChangesJob = savedFilesChanges()
            .onStart { emit(readSavedFiles()) }
            .onEach { savedFiles.value = it }
            .flowOn(ioContext)
            .launchIn(viewModelScope)
    }

    fun saveFile(name: String, text: String) {
        viewModelScope.launch {
            withContext(ioContext) {
                File(filesDir, "$name.txt").writeText(text)
            }
        }
    }

    fun deleteFile(savedFile: SavedFile) {
        File(filesDir, savedFile.name).delete()
    }

    private fun savedFilesChanges(): Flow<List<SavedFile>> = callbackFlow {
        val fileObserver = object : FileObserver(filesDir) {
            override fun onEvent(event: Int, path: String?) {
                val wroteFile = event and (DELETE or CLOSE_WRITE) != 0
                // Even just accessing/opening a file will hit this callback, which is not interesting.
                if (!wroteFile) {
                    return
                }
                offer(readSavedFiles())
            }
        }

        fileObserver.startWatching()

        awaitClose { fileObserver.stopWatching() }
    }

    private fun readSavedFiles(): List<SavedFile> {
        return filesDir.listFiles()!!
            .filter { file -> file.isFile }
            .map { file: File ->
                SavedFile(
                    name = file.name,
                    content = file.readText()
                )
            }
    }

    class Factory(
        private val initialFilesDirectory: FilesDirectory,
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FilesViewModel(initialFilesDirectory, context) as T
        }
    }
}

data class SavedFile(
    val name: String,
    val content: String
)

class SavedFileViewHolder(
    private val binding: SavedFileItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(savedFile: SavedFile, delete: (SavedFile) -> Unit) {
        binding.name.text = savedFile.name
        binding.content.text = savedFile.content
        binding.deleteFile.setOnClickListener { delete(savedFile) }
    }
}

object SavedFileDiffCallback : DiffUtil.ItemCallback<SavedFile>() {
    override fun areItemsTheSame(oldItem: SavedFile, newItem: SavedFile): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: SavedFile, newItem: SavedFile): Boolean {
        return oldItem == newItem
    }
}

class SavedFilesAdapter(
    private val delete: (SavedFile) -> Unit
) : ListAdapter<SavedFile, SavedFileViewHolder>(SavedFileDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedFileViewHolder {
        return LayoutInflater.from(parent.context)
            .let { inflater -> SavedFileItemBinding.inflate(inflater, parent, false) }
            .let { binding -> SavedFileViewHolder(binding) }
    }

    override fun onBindViewHolder(holder: SavedFileViewHolder, position: Int) {
        holder.bind(getItem(position), delete)
    }
}