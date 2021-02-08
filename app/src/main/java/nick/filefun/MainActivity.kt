package nick.filefun

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

class MainActivity : AppCompatActivity(R.layout.main_activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNavGraph()
    }

    private fun createNavGraph() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostContainer) as NavHostFragment

        navHostFragment.navController.apply {
            graph = createGraph(
                id = Navigation.id,
                startDestination = FilesDirFragment.Navigation.Destination.id,
            ) {
                fragment<FilesDirFragment>(FilesDirFragment.Navigation.Destination.id)
                fragment<PermissionsFragment>(PermissionsFragment.Navigation.Destination.id) {
                    action(Navigation.Action.emptyPermissionsGranted) {
                        destinationId = -1 // todo
                        navOptions {
                            popUpTo(PermissionsFragment.Navigation.Destination.id) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }
    }

    object Navigation {
        val id = IdGenerator.next()

        object Action {
            // todo: use actual relevant
            val emptyPermissionsGranted = IdGenerator.next()
        }
    }
}