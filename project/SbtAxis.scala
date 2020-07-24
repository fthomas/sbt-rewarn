import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._
import sbt.VirtualAxis.PlatformAxis
import sbt._
import sbt.internal.ProjectMatrix

case class SbtAxis(fullVersion: String, idSuffix: String, directorySuffix: String) extends VirtualAxis.WeakAxis {
  val scalaVersion: String =
    VersionNumber(fullVersion) match {
      case VersionNumber(Seq(0, 13, _*), _, _) => "2.10.7"
      case VersionNumber(Seq(1, _*), _, _)     => "2.12.10"
      case _                                   => sys.error(s"Unsupported sbt version: $fullVersion")
    }
}

object SbtAxis {

  def apply(version: String): SbtAxis =
    SbtAxis(version, version)
  def apply(version: String, fullVersion: String): SbtAxis =
    SbtAxis(fullVersion, "-" + version.replace('.', '_'), "-" + version)

  private val jvm: PlatformAxis = PlatformAxis("jvm", "", "jvm")

  implicit class RichProjectMatrix(val matrix: ProjectMatrix) extends AnyVal {
    def sbtPluginRow(axis: SbtAxis, process: Project => Project): ProjectMatrix =
      matrix.customRow(
        autoScalaLibrary = false,
        axisValues = Seq(axis, jvm),
        p =>
          process(
            p.settings(
              sbtPlugin := true,
              scalaVersion := axis.scalaVersion,
              pluginCrossBuild / sbtVersion := axis.fullVersion
            )
          )
      )
    def sbtScriptedRow(axis: SbtAxis, buildAxis: SbtAxis): ProjectMatrix =
      matrix.customRow(
        autoScalaLibrary = false,
        axisValues = Seq(axis, jvm),
        _.enablePlugins(ScriptedPlugin).settings(
          sbtPlugin := true,
          scalaVersion := axis.scalaVersion,
          pluginCrossBuild / sbtVersion := axis.fullVersion,
          publish / skip := true,
          compile / skip := true,
          scriptedDependencies := Def.taskDyn {
            if (insideCI.value) Def.task(())
            else Def.task(()).dependsOn(matrix.finder(buildAxis)(false) / publishLocal)
          }.value
        )
      )
  }

}
