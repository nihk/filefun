package nick.filefun

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nick.filefun.databinding.MediaItemBinding
import nick.filefun.databinding.MediaStoreFragmentBinding
import kotlin.coroutines.CoroutineContext

class MediaStoreFragment : Fragment(R.layout.media_store_fragment) {

    private lateinit var viewModel: MediaStoreViewModel
    private val deleteRequestLauncher = createDeleteRequestLauncher()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MediaStoreFragmentBinding.bind(view)
        val factory = MediaStoreViewModel.Factory(this, view.context, MediaType.Images)
        viewModel = ViewModelProvider(this, factory).get(MediaStoreViewModel::class.java)

        binding.mediaStoreGroup.setOnCheckedChangeListener { _, checkedId ->
            val mediaType = when (checkedId) {
                binding.images.id -> MediaType.Images
                binding.video.id -> MediaType.Video
                binding.audio.id -> MediaType.Audio
                else -> error("Unknown checkedId: $checkedId")
            }
            viewModel.setMediaType(mediaType)
        }

        val adapter = MediaAdapter(viewModel::delete)
        binding.documentsList.adapter = adapter

        viewModel.medias()
            .onEach { adapter.submitList(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.deleteRequests()
            .onEach { intentSender ->
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun createDeleteRequestLauncher(): ActivityResultLauncher<IntentSenderRequest> {
        return registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.runDeleteRequestResult(result.resultCode == Activity.RESULT_OK)
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

enum class MediaType(
    val uri: Uri,
    val idCol: String,
    val nameCol: String
) {
    // Note: apparently only VOLUME_EXTERNAL_PRIMARY can be modified on Android 10.
    // See: https://developer.android.com/training/data-storage/shared/media#add-item
    Images(
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        idCol = MediaStore.Images.Media._ID,
        nameCol = MediaStore.Images.Media.DISPLAY_NAME
    ),
    Video(
        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        idCol = MediaStore.Video.Media._ID,
        nameCol = MediaStore.Video.Media.DISPLAY_NAME
    ),
    Audio(
        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        idCol = MediaStore.Audio.Media._ID,
        nameCol = MediaStore.Audio.Media.DISPLAY_NAME
    )
}

@SuppressLint("StaticFieldLeak")
class MediaStoreViewModel(
    private val handle: SavedStateHandle,
    private val context: Context,
    private val initialMediaType: MediaType,
    private val ioContext: CoroutineContext
) : ViewModel() {

    private val medias = MutableStateFlow<List<Media>>(emptyList())
    fun medias(): StateFlow<List<Media>> = medias

    private val deleteRequests = MutableSharedFlow<IntentSender>()
    fun deleteRequests(): SharedFlow<IntentSender> = deleteRequests

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
            val idCold = cursor.getColumnIndexOrThrow(mediaType.idCol)
            val nameCol = cursor.getColumnIndexOrThrow(mediaType.nameCol)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCold)
                val name = cursor.getString(nameCol)
                medias += Media(
                    uri = ContentUris.withAppendedId(mediaType.uri, id),
                    name = name
                )
            }
        }
        return medias
    }

    fun delete(uri: Uri) {
        viewModelScope.launch(ioContext) {
            try {
                context.contentResolver.delete(
                    uri,
                    null,
                    null
                )
            } catch (throwable: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && throwable is RecoverableSecurityException
                ) {
                    handle.set(KEY_URI_TO_DELETE, uri)
                    deleteRequests.emit(throwable.userAction.actionIntent.intentSender)
                } else {
                    throw throwable
                }
            }
        }
    }

    fun runDeleteRequestResult(permissionGranted: Boolean) {
        val uri = handle.get<Uri>(KEY_URI_TO_DELETE)
            ?: error("Attempted to execute pending deletion of URI, but URI wasn't found")
        handle.remove<Uri>(KEY_URI_TO_DELETE)
        if (permissionGranted) {
            delete(uri)
        }
    }

    class Factory(
        private val owner: SavedStateRegistryOwner,
        private val context: Context,
        private val initialMediaType: MediaType,
        private val ioContext: CoroutineContext = Dispatchers.IO
    ) : AbstractSavedStateViewModelFactory(owner, null) {
        override fun <T : ViewModel?> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            @Suppress("UNCHECKED_CAST")
            return MediaStoreViewModel(handle, context, initialMediaType, ioContext) as T
        }
    }

    companion object {
        private const val KEY_URI_TO_DELETE = "uri_to_delete"
    }
}

data class Media(
    val uri: Uri,
    val name: String
)

class MediaViewHolder(private val binding: MediaItemBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(media: Media, delete: (Uri) -> Unit) {
        binding.name.text = media.name
        binding.delete.setOnClickListener { delete(media.uri) }
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

class MediaAdapter(
    private val delete: (Uri) -> Unit
) : ListAdapter<Media, MediaViewHolder>(MediaDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return LayoutInflater.from(parent.context)
            .let { inflater -> MediaItemBinding.inflate(inflater, parent, false) }
            .let { binding -> MediaViewHolder(binding) }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position), delete)
    }
}