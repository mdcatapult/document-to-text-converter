package io.mdcatapult.rawtext.extractors

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils

import org.apache.commons.io.FilenameUtils
import org.scalatest.BeforeAndAfter
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._
import scala.io.Source


class RawTextSpec extends FlatSpec with BeforeAndAfter {

  def getPath(file: String): String = Paths.get(getClass.getResource(file).toURI).toString

  implicit val config: Config = ConfigFactory.parseMap(Map[String, Any](
    "rawtext.to.path" → "./test"
  ).asJava)

  val docFiles: List[(String, String)] = List[(String, String)](
    ("/test_doc.doc", "test_doc.txt"),
    ("/test_docx.docx", "test_docx.txt"),
    ("/test_office_open_docx.docx", "test_office_open_docx.txt"),
    ("/test_odt.odt", "test_odt.txt"),
    ("/test_ods.ods", "test_ods.txt"),
    ("/test_xls.xls", "test_xls.txt"),
    ("/test_xlsx.xlsx", "test_xlsx.txt"),
    ("/test_office_open_xlsx.xlsx", "test_office_open_xlsx.txt"),
  )

  docFiles foreach {file: (String, String) => {

    f"The file ${file._1}" should f"generate a raw text file called ${file._2}" in {
      val result = new RawText(getPath(file._1)).getRawTextFilePath
      assert(FilenameUtils.getName(result) == file._2)
    }

    // TODO - finish tests to check contents of extracted test files
//    it should "extract successfully " in {
//      val extractor = new RawText(getPath(file._1))
//      val target = extractor.extract
//
//      val lines = Source.fromFile(target).getLines.toList
//      println(lines.length)
//      lines foreach {line: String => println(line)}
//      println("**** Finished ****")
//
//      assert(new File(target).exists())
//      assert(true === FileUtils.contentEquals(new File(target), new File(getPath("/test_output.txt"))))
//
//    }

  }}

}
