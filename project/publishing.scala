import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.Keys.*
import sbt.*

//noinspection ScalaFileName
object DeployApp extends AutoPlugin {

  import com.typesafe.sbt.packager.SettingsHelper
  import com.typesafe.sbt.packager.universal.UniversalPlugin
  import UniversalPlugin.autoImport.{Universal, UniversalDocs}

  override def requires: Plugins = UniversalPlugin && JavaAppPackaging

  override def projectSettings: Seq[Setting[_]] =
    SettingsHelper.makeDeploymentSettings(Universal, Universal / packageBin, "zip") ++
      SettingsHelper.makeDeploymentSettings(UniversalDocs, UniversalDocs / packageBin, "zip") ++ Seq(
      Universal / target := baseDirectory.value.getParentFile / "target" / "universal"
    )
}
