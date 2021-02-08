package nick.filefun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nick.filefun.databinding.MediaItemBinding
import nick.filefun.databinding.MediaStoreFragmentBinding

class MediaStoreFragment : Fragment(R.layout.media_store_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MediaStoreFragmentBinding.bind(view)
        val factory = MediaStoreViewModel.Factory(view.context, MediaType.Video)
        val viewModel = ViewModelProvider(this, factory).get(MediaStoreViewModel::class.java)

        binding.mediaStoreGroup.setOnCheckedChangeListener { _, checkedId ->
            val mediaType = when (checkedId) {
                binding.video.id -> MediaType.Video
                binding.audio.id -> MediaType.Audio
                binding.images.id -> MediaType.Images
                binding.documents.id -> MediaType.Documents
                else -> error("Unknown checkedId: $checkedId")
            }
            viewModel.setMediaType(mediaType)
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}

enum class MediaType {
    Video,
    Audio,
    Images,
    Documents
}

@SuppressLint("StaticFieldLeak")
class MediaStoreViewModel(
    private val context: Context,
    private val initialMediaType: MediaType
) : ViewModel() {

    private var mediaType: MediaType = initialMediaType

    fun setMediaType(mediaType: MediaType) {
        this.mediaType = mediaType
    }

    class Factory(
        private val context: Context,
        private val initialMediaType: MediaType
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaStoreViewModel(context, initialMediaType) as T
        }
    }
}

data class Media(
    val name: String
)

class MediaViewHolder(private val binding: MediaItemBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(media: Media) {
        // todo
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
            .let { inflater -> MediaItemBinding.inflate(inflater) }
            .let { binding -> MediaViewHolder(binding) }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}