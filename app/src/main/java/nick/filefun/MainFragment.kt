package nick.filefun

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nick.filefun.databinding.MainFragmentBinding

class MainFragment : Fragment(R.layout.main_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MainFragmentBinding.bind(view)
        binding.appSpecificFiles.setOnClickListener {
            findNavController().navigate(FilesFragment.Navigation.Destination.id)
        }
        binding.mediaStore.setOnClickListener {
            val arguments = PermissionsFragment.bundle(
                requestedPermissions = mutableListOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ).apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                destinationAfterPermissionsGranted = MediaStoreFragment.Navigation.Destination.id
            )
            findNavController().navigate(PermissionsFragment.Navigation.Destination.id, arguments)
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }
}