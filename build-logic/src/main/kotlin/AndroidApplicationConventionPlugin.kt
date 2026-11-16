import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        // 为使用这个约定插件的模块应用 Android Application 插件
        target.pluginManager.apply("com.android.application")

        // 配置该模块的 android { ... }
        target.extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(36) {
                    minorApiLevel = 1
                }
            }

            defaultConfig {
                minSdk = 24
            }
        }
    }
}
