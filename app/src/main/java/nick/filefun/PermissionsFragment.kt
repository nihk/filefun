package nick.filefun

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class PermissionsFragment : Fragment() {

    private val requestedPermissions: List<String> get() = arguments?.getStringArrayList(KEY_REQUESTED_PERMISSIONS)!!
    private val navAction: Int get() = arguments?.getInt(KEY_NAV_ACTION, -1)!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermissions()) {
            executeNavAction()
        } else {
            val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    executeNavAction()
                } else {
                    Toast.makeText(requireContext(), "Permissions were not granted :(", Toast.LENGTH_LONG)
                        .show()
                    requireActivity().finish()
                }
            }

            permissionRequest.launch(requestedPermissions.toTypedArray())
        }
    }

    private fun executeNavAction() {
        if (navAction == POP_ACTION) {
            findNavController().popBackStack()
        } else {
            findNavController().navigate(navAction)
        }
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
        private const val KEY_NAV_ACTION = "nav_action"
        private const val POP_ACTION = -1

        fun bundle(requestedPermissions: List<String>, navAction: Int = POP_ACTION): Bundle {
            return bundleOf(
                KEY_REQUESTED_PERMISSIONS to requestedPermissions,
                KEY_NAV_ACTION to navAction
            )
        }
    }
}