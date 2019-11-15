package io.mdcatapult.rawtext.extractors

import java.nio.file.Paths

import better.files.Dsl.pwd
import better.files.{File ⇒ ScalaFile}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.util.DirectoryDelete
import org.apache.commons.io.FilenameUtils
import org.scalatest.{BeforeAndAfterAll, FlatSpec}


class RawTextSpec extends FlatSpec with BeforeAndAfterAll with DirectoryDelete {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  root: "test"
      |  local {
      |    target-dir: "local"
      |    temp-dir: "ingress"
      |  }
      |  remote {
      |    target-dir: "remote"
      |    temp-dir: "remote-ingress"
      |  }
      |  archive {
      |    target-dir: "archive"
      |  }
      |}
      |rawtext {
      |  to {
      |     path: "derivatives"
      |   }
      |}
    """.stripMargin)

  def getPath(parentDir: String, file: String): String = Paths.get(parentDir, file).toString

  val docFiles: List[(String, String)] = List[(String, String)](
    ("test_doc.doc", "test_doc.txt"),
    ("test_docx.docx", "test_docx.txt"),
    ("test_office_open_docx.docx", "test_office_open_docx.txt"),
    ("test_odt.odt", "test_odt.txt"),
    ("test_ods.ods", "test_ods.txt"),
    ("test_xls.xls", "test_xls.txt"),
    ("test_xlsx.xlsx", "test_xlsx.txt"),
    ("test_office_open_xlsx.xlsx", "test_office_open_xlsx.txt"),
  )

  docFiles foreach {file: (String, String) => {

    f"The local file ${file._1}" should f"generate a raw text file called ${file._2}" in {
      val result = new RawText(getPath(config.getString("doclib.local.target-dir"), file._1)).getRawTextFilePath
      val txtName = ScalaFile(file._1).nameWithoutExtension.toString + ".txt"
      assert(result == s"test/ingress/derivatives/raw_text-${file._1}/$txtName")
      assert(FilenameUtils.getName(result) == file._2)
    }

    f"The remote file ${file._1}" should "have the correct target path generated" in {
      val result = new RawText(getPath(config.getString("doclib.remote.target-dir"), file._1)).getRawTextFilePath
      val txtName = ScalaFile(file._1).nameWithoutExtension.toString + ".txt"
      assert(result == s"test/ingress/derivatives/remote/raw_text-${file._1}/$txtName")
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

  }
  }
  "A derived doc source in ingress" should "have the correct parent path when extracted" in {
    val source = "local/derivatives/ebi/supplementary/unarchived-PMC123456.zip/information.xml"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/ebi/supplementary/unarchived-PMC123456.zip/raw_text-information.xml")
  }

  "A local doc source" should "have the correct parent path when extracted" in {
    val source = "local/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/a/path/to/raw_text-file.pdf")
  }

  "A remote doc source" should "have the correct path parent when extracted" in {
    val source = "remote/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/remote/a/path/to/raw_text-file.pdf")
  }

  "A remote derivative doc source" should "have the correct parent path when extracted" in {
    val source = "local/derivatives/remote/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/remote/a/path/to/raw_text-file.pdf")
  }

  "A local file" should "be extracted to the correct doclib folders" in {
    val source = "local/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/a/path/raw_text-test_doc.doc/raw.txt")
  }

  "A remote file" should "be extracted to the correct doclib folders" in {
    val source = "remote/http/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/remote/http/a/path/raw_text-test_doc.doc/raw.txt")
  }

  "A remote derivative file" should "be extracted to the correct doclib folders" in {
    val source = "local/derivatives/remote/http/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/remote/http/a/path/raw_text-test_doc.doc/raw.txt")
  }

  override def afterAll(): Unit = {
    deleteDirectories(List((pwd/"test"/"ingress")))
  }
}
