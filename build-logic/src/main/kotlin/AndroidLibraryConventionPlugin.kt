import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.library")

        target.extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37)
            }

            defaultConfig {
                minSdk = 24
            }
        }
    }
}
