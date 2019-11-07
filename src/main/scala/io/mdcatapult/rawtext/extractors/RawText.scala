package io.mdcatapult.rawtext.extractors

import java.io._
import java.nio.file.Paths

import com.typesafe.config.Config
import io.mdcatapult.doclib.loader.SourceLoader
import io.mdcatapult.doclib.util._
import org.apache.commons.io.FilenameUtils

class RawText(source: String)(implicit config: Config) extends TargetPath {

  // set the target path using the source parent directory path and the configured `to` path
  lazy val targetPath: String = getTargetPath(FilenameUtils.getFullPath(source), config.getString("rawtext.to.path"))

  /**
    * Concatenates the targetPath, source file baseName and `.txt` extension
    * to give the new raw text file path.
    *
    * @return the new raw test filepath as a String
    */
  def getRawTextFilePath: String = {
    val baseName = FilenameUtils.getBaseName(source)
    Paths.get(config.getString("doclib.root"), targetPath, baseName + ".txt").toString
  }


  /**
    * Extract the source file to a raw text file.
    *
    * @return the new raw text filepath as a String
    */
  def extract : String = {
    val rawTextFilePath = getRawTextFilePath
    val rawTextFile = new File(rawTextFilePath)
    rawTextFile.getParentFile.mkdirs()
    val bw = new BufferedWriter(new FileWriter(rawTextFile))
    bw.write(SourceLoader.load(source).mkString("\n"))
    bw.close()
    rawTextFilePath
  }
}
