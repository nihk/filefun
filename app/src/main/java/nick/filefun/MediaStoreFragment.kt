package nick.filefun

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import nick.filefun.databinding.MediaStoreFragmentBinding

class MediaStoreFragment : Fragment(R.layout.media_store_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MediaStoreFragmentBinding.bind(view)
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}