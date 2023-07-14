package io.mdcatapult.doclib.handlers

import io.mdcatapult.doclib.consumer.HandlerResult
import io.mdcatapult.doclib.models.DoclibDoc
import org.mongodb.scala.result.InsertManyResult

case class RawTextHandlerResult(doclibDoc: DoclibDoc,
                                persisted: InsertManyResult,
                                newFilePath: String) extends HandlerResult