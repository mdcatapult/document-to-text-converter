import sbtrelease.ReleaseStateTransformations._
import Release._

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val root = (project in file("."))
  .settings(
    name              := "consumer-raw-text",
    scalaVersion      := "2.13.3",
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint",
      "-Xfatal-warnings",
    ),
    useCoursier := false,
    resolvers         ++= Seq(
      "MDC Nexus Public" at "https://nexus.wopr.inf.mdc/repository/maven-public/",
      "MDC Nexus Snapshots" at "https://nexus.wopr.inf.mdc/repository/maven-snapshots/",
      "Maven Public" at "https://repo1.maven.org/maven2"),
    credentials       += {
      sys.env.get("NEXUS_PASSWORD") match {
        case Some(p) =>
          Credentials("Sonatype Nexus Repository Manager", "nexus.wopr.inf.mdc", "gitlab", p)
        case None =>
          Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
libraryDependencies ++= {
  val doclibCommonVersion = "3.1.1-SNAPSHOT"
  val kleinSourceVersion = "1.0.6"

  val configVersion = "1.4.1"
  val akkaVersion = "2.6.18"
  val catsVersion = "2.6.1"
  val scalacticVersion = "3.2.10"
  val scalaTestVersion = "3.2.11"
  val scalaLoggingVersion = "3.9.4"
  val logbackClassicVersion = "1.2.10"

  Seq(
    "org.scalactic" %% "scalactic"                   % scalacticVersion,
     "org.scalatest" %% "scalatest"                  % scalaTestVersion % Test,
     "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
     "ch.qos.logback" % "logback-classic"            % logbackClassicVersion,
     "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
     "com.typesafe" % "config"                       % configVersion,
     "org.typelevel" %% "cats-kernel"                % catsVersion,
     "org.typelevel" %% "cats-core"                  % catsVersion,
     "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion,
     "io.mdcatapult.klein" %% "source"               % kleinSourceVersion
//     "org.xerial" % "sqlite-jdbc"                      % "3.30.1"
  )}.map(
      _.exclude(org = "com.google.protobuf", name = "protobuf-java")
        .exclude(org = "com.typesafe.play", name = "shaded-asynchttpclient")
    )
  )
  .settings(
    assemblyJarName := "consumer.jar",
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST") => MergeStrategy.discard
      case PathList("META-INF", "jpms.args") => MergeStrategy.discard
      case PathList("com", "sun", _*) => MergeStrategy.first
      case PathList("javax", "servlet", _*) => MergeStrategy.first
      case PathList("javax", "activation", _*) => MergeStrategy.first
      case PathList("org", "apache", "commons", _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last endsWith ".DSA" => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last endsWith ".SF" => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.first
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case n if n.startsWith("logback.xml") => MergeStrategy.first
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
