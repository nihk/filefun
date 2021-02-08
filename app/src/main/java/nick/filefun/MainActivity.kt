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
                startDestination = MainFragment.Navigation.Destination.id,
            ) {
                fragment<MainFragment>(MainFragment.Navigation.Destination.id) {
                    action(MainFragment.Navigation.Action.toAppSpecificFiles) {
                        destinationId = FilesFragment.Navigation.Destination.id
                    }
                }
                fragment<FilesFragment>(FilesFragment.Navigation.Destination.id)
                fragment<PermissionsFragment>(PermissionsFragment.Navigation.Destination.id)
            }
        }
    }

    object Navigation {
        val id = IdGenerator.next()
    }
}