package nick.filefun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class PermissionsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermissions()) {
            navigateToCamera()
        } else {
            val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    navigateToCamera()
                } else {
                    Toast.makeText(requireContext(), "Permissions were not granted :(", Toast.LENGTH_LONG)
                        .show()
                    requireActivity().finish()
                }
            }

            permissionRequest.launch(REQUESTED_PERMISSIONS)
        }
    }

    private fun navigateToCamera() {
        findNavController().navigate(Navigation.Action.granted)
    }

    private fun hasPermissions(): Boolean {
        return REQUESTED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
        object Action {
            val granted = IdGenerator.next()
        }
    }

    companion object {
        private val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )
    }
}