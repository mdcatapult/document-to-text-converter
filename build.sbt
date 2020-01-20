import sbtrelease.ReleaseStateTransformations._
import Release._

lazy val configVersion = "1.3.2"
lazy val akkaVersion = "2.5.25"
lazy val catsVersion = "2.0.0"
lazy val opRabbitVersion = "2.1.0"
lazy val mongoVersion = "2.5.0"
lazy val awsScalaVersion = "0.8.1"
lazy val tikaVersion = "1.21"
lazy val doclibCommonVersion = "0.0.34"

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val root = (project in file("."))
  .settings(
    name              := "consumer-raw-text",
    scalaVersion      := "2.12.8",
    scalacOptions     += "-Ypartial-unification",
    resolvers         ++= Seq(
      "MDC Nexus Public" at "https://nexus.mdcatapult.io/repository/maven-public/",
      "MDC Nexus Snapshots" at "https://nexus.mdcatapult.io/repository/maven-snapshots/",
      "Maven Public" at "https://repo1.maven.org/maven2"),
    credentials       += {
      val nexusPassword = sys.env.get("NEXUS_PASSWORD")
      if ( nexusPassword.nonEmpty ) {
        Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", nexusPassword.get)
      } else {
        Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic"                  % "3.0.5",
      "org.scalatest" %% "scalatest"                  % "3.0.5" % "test",
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "com.typesafe" % "config"                       % configVersion,
      "org.typelevel" %% "cats-macros"                % catsVersion,
      "org.typelevel" %% "cats-kernel"                % catsVersion,
      "org.typelevel" %% "cats-core"                  % catsVersion,
      "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion,
      "commons-io" % "commons-io"                     % "2.6"
    ),

  )
  .settings(
    assemblyJarName := "consumer.jar",
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST") => MergeStrategy.discard
      case PathList("META-INF", "jpms.args") => MergeStrategy.discard
      case PathList("com", "sun", xs @ _*) => MergeStrategy.first
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.first
      case PathList("org", "apache", "commons", xs @ _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", xs @ _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last endsWith ".DSA" => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last endsWith ".SF" => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.concat
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case meta(_) => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
  .settings(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      getShortSha,
      writeReleaseVersionFile,
      commitAllRelease,
      tagRelease,
      runAssembly,
      setNextVersion,
      writeNextVersionFile,
      commitAllNext,
      pushChanges
    )
  )
