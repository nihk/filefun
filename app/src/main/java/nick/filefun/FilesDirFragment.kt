package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nick.filefun.databinding.FilesDirFragmentBinding
import nick.filefun.databinding.SavedFileItemBinding
import java.io.File
import kotlin.coroutines.CoroutineContext

class FilesDirFragment : Fragment(R.layout.files_dir_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FilesDirFragmentBinding.bind(view)
        val factory = FilesDirViewModel.Factory(view.context.applicationContext)
        val viewModel = ViewModelProvider(this, factory).get(FilesDirViewModel::class.java)

        binding.saveFile.setOnClickListener {
            viewModel.saveFile(
                name = binding.fileNameInput.text.toString(),
                text = binding.fileContent.text.toString()
            )
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

@SuppressLint("StaticFieldLeak")
class FilesDirViewModel(
    private val context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : ViewModel() {

    private val filesDir: File by lazy { context.filesDir }

    private val savedFiles = MutableStateFlow<List<SavedFile>>(emptyList())
    fun savedFiles(): StateFlow<List<SavedFile>> = savedFiles

    init {
        savedFilesChanges()
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
                offer(readSavedFiles())
            }
        }

        fileObserver.startWatching()

        awaitClose { fileObserver.stopWatching() }
    }

    private fun readSavedFiles(): List<SavedFile> {
        return filesDir.listFiles()!!.map { file: File ->
            SavedFile(
                name = file.name,
                content = file.readText()
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FilesDirViewModel(context) as T
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
            .let { SavedFileItemBinding.inflate(it) }
            .let { SavedFileViewHolder(it) }
    }

    override fun onBindViewHolder(holder: SavedFileViewHolder, position: Int) {
        holder.bind(getItem(position), delete)
    }
}