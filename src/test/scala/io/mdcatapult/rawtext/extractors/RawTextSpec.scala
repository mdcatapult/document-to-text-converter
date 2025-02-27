/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.rawtext.extractors

import java.nio.file.Paths

import better.files.Dsl.pwd
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.util.path.DirectoryDeleter.deleteDirectories
import org.apache.commons.io.FilenameUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.BeforeAndAfterAll

class RawTextSpec extends AnyFlatSpec with BeforeAndAfterAll {

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
      |  derivative {
      |    target-dir: "derivatives"
      |  }
      |}
      |consumer {
      |  name: rawtext
      |}
    """.stripMargin)

  def getPath(parentDir: String, file: String): String = Paths.get(parentDir, file).toString

  val docFiles: List[(String, String)] = List[(String, String)](
    "test_doc.doc" -> "raw.txt",
    "test_docx.docx" -> "raw.txt",
    "test_office_open_docx.docx" -> "raw.txt",
    "test_odt.odt" -> "raw.txt",
    "test_ods.ods" -> "raw.txt",
    "test_xls.xls" -> "raw.txt",
    "test_xlsx.xlsx" -> "raw.txt",
    "test_office_open_xlsx.xlsx" -> "raw.txt",
  )

  val txtName: String = "raw.txt"
  docFiles foreach { file: (String, String) => {

      f"The local file ${file._1}" should f"generate a raw text file called ${file._2}" in {
        val result = new RawText(getPath(config.getString("doclib.local.target-dir"), file._1)).relativeFilePath

        assert(result == s"ingress/derivatives/rawtext-${file._1}/$txtName")
        assert(FilenameUtils.getName(result) == file._2)
      }

    f"The remote file ${file._1}" should "have the correct target path generated" in {
      val result = new RawText(getPath(config.getString("doclib.remote.target-dir"), file._1)).relativeFilePath

        assert(result == s"ingress/derivatives/remote/rawtext-${file._1}/$txtName")
        assert(FilenameUtils.getName(result) == file._2)
      }
    }
  }
  "A derived doc source in ingress" should "have the correct parent path when extracted" in {
    val source = "local/derivatives/ebi/supplementary/unarchived-PMC123456.zip/information.xml"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/ebi/supplementary/unarchived-PMC123456.zip/rawtext-information.xml")
  }

  "A local doc source" should "have the correct parent path when extracted" in {
    val source = "local/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/a/path/to/rawtext-file.pdf")
  }

  "A remote doc source" should "have the correct path parent when extracted" in {
    val source = "remote/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/remote/a/path/to/rawtext-file.pdf")
  }

  "A remote derivative doc source" should "have the correct parent path when extracted" in {
    val source = "local/derivatives/remote/a/path/to/file.pdf"
    val targetPath = new RawText(source).targetPath
    assert(targetPath == "ingress/derivatives/remote/a/path/to/rawtext-file.pdf")
  }

  "A local file" should "be extracted to the correct doclib folders" in {
    val source = "local/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/a/path/rawtext-test_doc.doc/raw.txt")
  }

  "A remote file" should "be extracted to the correct doclib folders" in {
    val source = "remote/http/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/remote/http/a/path/rawtext-test_doc.doc/raw.txt")
  }

  "A remote derivative file" should "be extracted to the correct doclib folders" in {
    val source = "local/derivatives/remote/http/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.extract
    assert(path == "ingress/derivatives/remote/http/a/path/rawtext-test_doc.doc/raw.txt")
  }

  "A derivative of a derivative file" should "be extracted to the correct doclib folders" in {
    val source = "local/derivatives/derivatives/remote/http/a/path/test_doc.doc"
    val rawText = new RawText(source)
    val path = rawText.targetPath
    assert(path == "ingress/derivatives/remote/http/a/path/rawtext-test_doc.doc")
  }

  override def afterAll(): Unit = {
    deleteDirectories(List(pwd/"test"/"ingress"))
  }
}
