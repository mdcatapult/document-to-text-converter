package io.mdcatapult.rawtext.extractors

import java.io._
import java.nio.file.Paths

import com.typesafe.config.Config
import io.mdcatapult.doclib.loader.SourceLoader
import org.apache.commons.io.FilenameUtils

class RawText(source: String)(implicit config: Config) {

  // set the target path using the source parent directory path and the configured `to` path
  lazy val targetPath: String = getTargetPath(source, config.getString("rawtext.to.path"), Some("raw_text"))
  private val doclibRoot: String = s"${config.getString("doclib.root").replaceFirst("""/+$""", "")}/"

  def getAbsPath(path: String): String = {
    Paths.get(doclibRoot, path).toAbsolutePath.toString
  }

  /**
   * determines common root paths for two path string
   * @param paths List[String]
   * @return String common path component
   */
  protected def commonPath(paths: List[String]): String = {
    val SEP = "/"
    val BOUNDARY_REGEX = s"(?=[$SEP])(?<=[^$SEP])|(?=[^$SEP])(?<=[$SEP])"
    def common(a: List[String], b: List[String]): List[String] = (a, b) match {
      case (aa :: as, bb :: bs) if aa equals bb => aa :: common(as, bs)
      case _ => Nil
    }
    if (paths.length < 2) paths.headOption.getOrElse("")
    else paths.map(_.split(BOUNDARY_REGEX).toList).reduceLeft(common).mkString
  }

  /**
   * generate new file path maintaining file path from origin but allowing for intersection of common root paths
   * @param source String
   * @return String full path to new target
   */
  def getTargetPath(source: String, base: String, prefix: Option[String] = None): String = {
    val targetRoot = base.replaceAll("/+$", "")
    val regex = """(.*)/(.*)$""".r
    source match {
      case regex(path, file) =>
        val c = commonPath(List(targetRoot, path))
        val targetPath  = scrub(path.replaceAll(s"^$c", "").replaceAll("^/+|/+$", ""))
        Paths.get(config.getString("doclib.local.temp-dir"), targetRoot, targetPath, s"${prefix.getOrElse("")}-$file").toString
      case _ => source
    }
  }

  def scrub(path: String):String  = path match {
    case path if path.startsWith(config.getString("doclib.local.target-dir")) =>
      scrub(path.replaceFirst(s"^${config.getString("doclib.local.target-dir")}/*", ""))
    case path if path.startsWith(config.getString("rawtext.to.path"))  =>
      scrub(path.replaceFirst(s"^${config.getString("rawtext.to.path")}/*", ""))
    case _ => path
  }

  /**
    * Concatenates the targetPath, source file baseName and `.txt` extension
    * to give the new raw text file path.
    *
    * @return the new raw test filepath as a String
    */
  def getRawTextFilePath: String = {
    //TODO This method may or may not be needed depending on whether we go for raw.txt or filename.txt
    val baseName = FilenameUtils.getBaseName(source)
    Paths.get(config.getString("doclib.root"), targetPath, baseName + ".txt").toString
  }


  /**
    * Extract the source file to a raw text file.
    *
    * @return the new raw text filepath as a String
    */
  def extract : String = {
    // TODO Not clear whether file should be 'raw.txt' or 'filename.txt'
    val relPath = Paths.get(targetPath, "raw.txt").toString
    val sourcePath = Paths.get(getAbsPath(source))
    val target = new File(getAbsPath(relPath))
    target.getParentFile.mkdirs()
    val bw = new BufferedWriter(new FileWriter(target))
    bw.write(SourceLoader.load(sourcePath.toString).mkString("\n"))
    bw.close()
    relPath
  }

}
