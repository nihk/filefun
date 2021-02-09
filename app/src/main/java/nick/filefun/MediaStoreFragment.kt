package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import nick.filefun.databinding.MediaItemBinding
import nick.filefun.databinding.MediaStoreFragmentBinding
import kotlin.coroutines.CoroutineContext

class MediaStoreFragment : Fragment(R.layout.media_store_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MediaStoreFragmentBinding.bind(view)
        val factory = MediaStoreViewModel.Factory(view.context, MediaType.Images)
        val viewModel = ViewModelProvider(this, factory).get(MediaStoreViewModel::class.java)

        binding.mediaStoreGroup.setOnCheckedChangeListener { _, checkedId ->
            val mediaType = when (checkedId) {
                binding.images.id -> MediaType.Images
                binding.video.id -> MediaType.Video
                binding.audio.id -> MediaType.Audio
                else -> error("Unknown checkedId: $checkedId")
            }
            viewModel.setMediaType(mediaType)
        }

        val adapter = MediaAdapter()
        binding.documentsList.adapter = adapter

        viewModel.medias()
            .onEach { adapter.submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

enum class MediaType(
    val uri: Uri,
    val nameCol: String
    ) {
    Images(
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        nameCol = MediaStore.Images.ImageColumns.DISPLAY_NAME
    ),
    Video(
        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        nameCol = MediaStore.Video.VideoColumns.DISPLAY_NAME
    ),
    Audio(
        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        nameCol = MediaStore.Audio.AudioColumns.DISPLAY_NAME
    )
}

@SuppressLint("StaticFieldLeak")
class MediaStoreViewModel(
    private val context: Context,
    private val initialMediaType: MediaType,
    private val ioContext: CoroutineContext
) : ViewModel() {

    private val medias = MutableStateFlow<List<Media>>(emptyList())
    fun medias(): StateFlow<List<Media>> = medias

    private var mediaType: MediaType = initialMediaType
    private var mediaStoreJob: Job? = null

    init {
        observeMediaStore()
    }

    fun setMediaType(mediaType: MediaType) {
        mediaStoreJob?.cancel()
        this.mediaType = mediaType
        observeMediaStore()
    }

    private fun observeMediaStore() {
        mediaStoreJob = mediaStore()
            .onStart { emit(queryMedia()) }
            .onEach { medias.value = it }
            .flowOn(ioContext)
            .launchIn(viewModelScope)
    }

    private fun mediaStore(): Flow<List<Media>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                offer(queryMedia())
            }
        }

        context.contentResolver.registerContentObserver(mediaType.uri, true, observer)

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun queryMedia(): List<Media> {
        val medias = mutableListOf<Media>()
        context.contentResolver.query(
            mediaType.uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(mediaType.nameCol))
                medias += Media(name)
            }
        }
        return medias
    }

    class Factory(
        private val context: Context,
        private val initialMediaType: MediaType,
        private val ioContext: CoroutineContext = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaStoreViewModel(context, initialMediaType, ioContext) as T
        }
    }
}

data class Media(
    val name: String
)

class MediaViewHolder(private val binding: MediaItemBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(media: Media) {
        binding.name.text = media.name
    }
}

object MediaDiffCallback : DiffUtil.ItemCallback<Media>() {
    override fun areItemsTheSame(oldItem: Media, newItem: Media): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: Media, newItem: Media): Boolean {
        return oldItem == newItem
    }
}

class MediaAdapter : ListAdapter<Media, MediaViewHolder>(MediaDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return LayoutInflater.from(parent.context)
            .let { inflater -> MediaItemBinding.inflate(inflater, parent, false) }
            .let { binding -> MediaViewHolder(binding) }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}