package nick.filefun

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
            findNavController().navigate(Navigation.Action.toAppSpecificFiles)
        }
    }

    object Navigation {
        object Destination {
            val id = IdGenerator.next()
        }
        object Action {
            val toAppSpecificFiles = IdGenerator.next()
        }
    }
}