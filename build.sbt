// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.15"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "KU Leuven"

val chiselVersion = "6.6.0"

lazy val root = (project in file("."))
  .settings(
    name := "chisel-activation-functions",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "org.scalatest" %% "scalatest" % "3.2.16" % "test",
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )

// there are 6 packages within this project: DyT, gelu, GroupNorm, hardfloat, silu and toplevel