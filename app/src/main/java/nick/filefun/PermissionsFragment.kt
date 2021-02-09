package nick.filefun

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController

class PermissionsFragment : Fragment() {

    private val requestedPermissions: List<String> get() = arguments?.getStringArray(KEY_REQUESTED_PERMISSIONS)?.toList()!!
    private val destinationAfterPermissionsGranted: Int get() = arguments?.getInt(KEY_DESTINATION_AFTER_PERMISSIONS_GRANTED, -1)!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermissions()) {
            navigateToDestination()
        } else {
            val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    navigateToDestination()
                } else {
                    Toast.makeText(requireContext(), "Permissions were not granted :(", Toast.LENGTH_LONG)
                        .show()
                    findNavController().popBackStack()
                }
            }

            permissionRequest.launch(requestedPermissions.toTypedArray())
        }
    }

    private fun navigateToDestination() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(findNavController().currentDestination?.id!!, true)
            .build()
        findNavController().navigate(destinationAfterPermissionsGranted, null, navOptions)
    }

    private fun hasPermissions(): Boolean {
        return requestedPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
    }

    companion object {
        private const val KEY_REQUESTED_PERMISSIONS = "requested_permissions"
        private const val KEY_DESTINATION_AFTER_PERMISSIONS_GRANTED = "destination_after_permissions_granted"

        fun bundle(requestedPermissions: List<String>, destinationAfterPermissionsGranted: Int): Bundle {
            return bundleOf(
                KEY_REQUESTED_PERMISSIONS to requestedPermissions.toTypedArray(),
                KEY_DESTINATION_AFTER_PERMISSIONS_GRANTED to destinationAfterPermissionsGranted
            )
        }
    }
}