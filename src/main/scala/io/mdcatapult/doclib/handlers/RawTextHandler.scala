package io.mdcatapult.doclib.handlers

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import io.mdcatapult.doclib.consumer.{ConsumerHandler, GenericHandlerReturn}
import io.mdcatapult.doclib.flag.{FlagContext, MongoFlagStore}
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.doclib.models.{ConsumerNameAndQueue, DoclibDoc, Origin, ParentChildMapping}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.rawtext.extractors.RawText
import io.mdcatapult.util.concurrency.LimitedExecution
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.result.InsertManyResult

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class RawTextHandler(prefetch: Sendable[PrefetchMsg],
                     supervisor: Sendable[SupervisorMsg],
                     val readLimiter: LimitedExecution)
                    (implicit ex: ExecutionContext,
                     config: Config,
                     collection: MongoCollection[DoclibDoc],
                     derivativesCollection: MongoCollection[ParentChildMapping])
  extends ConsumerHandler[DoclibMsg] {

  private val flags = new MongoFlagStore(version, docExtractor, collection, nowUtc)

  private implicit val consumerNameAndQueue: ConsumerNameAndQueue =
    ConsumerNameAndQueue(config.getString("consumer.name"), config.getString("consumer.queue"))

  /**
    * handler of raw text
    *
    * @param msg IncomingMsg to process
    * @param key routing key from rabbitmq
    * @return
    */
  def handle(msg: DoclibMsg, key: String): Future[Option[GenericHandlerReturn]] = {
    logReceived(msg.id)

    val flagContext: FlagContext = flags.findFlagContext(Some(consumerNameAndQueue.name))

    val rawTextProcess = for {
      doc <- OptionT(findDocById(collection, msg.id, readLimiter))
      if !docExtractor.isRunRecently(doc)
      started: UpdatedResult <- OptionT.liftF(flagContext.start(doc))
      // TODO - validate mimetype here??
      newFilePath <- OptionT(extractRawText(doc.source))
      _ <- OptionT(persist(doc, newFilePath))
      _ <- OptionT(enqueue(newFilePath, doc))
      _ <- OptionT.liftF(flagContext.end(doc, noCheck = started.changesMade))
    } yield GenericHandlerReturn(doc, Some(List(newFilePath)))

    postHandleProcess(
      message = msg,
      handlerReturn = rawTextProcess.value,
      supervisorQueueOpt = Some(supervisor),
      flagContext = flagContext,
      collectionOpt = Some(collection)
    )
  }

  def enqueue(newFilePath: String, doc: DoclibDoc): Future[Option[Boolean]] = {
    // Let prefetch know that it is an rawtext derivative
    val derivativeMetadata = List[MetaValueUntyped](MetaString("derivative.type", config.getString("consumer.name")))
    prefetch.send(PrefetchMsg(
      source = newFilePath,
      origins = Some(List(Origin(
        scheme = "mongodb",
        metadata = Some(List(
          MetaString("db", config.getString("mongo.doclib-database")),
          MetaString("collection", config.getString("mongo.documents-collection")),
          MetaString("_id", doc._id.toHexString),
        )),
      ))),
      tags = doc.tags,
      metadata = Some(doc.metadata.getOrElse(Nil) ::: derivativeMetadata),
      derivative = Some(true)
    ))
    Future.successful(Some(true))
  }

  def persist(doc: DoclibDoc, newFilePath: String): Future[Option[InsertManyResult]] = {
    val derivativesFromPaths = createDerivativesFromPaths(doc, List(newFilePath))
    derivativesCollection.insertMany(derivativesFromPaths).toFutureOption()
  }

  def extractRawText(source: String): Future[Option[String]] =
    Try(new RawText(source).extract) match {
      case Success(r) => Future.successful(Some(r))
      case Failure(e) => throw e
    }

  /**
    * Create list of parent child mappings
    *
    * @param doc   DoclibDoc
    * @param paths List[String]
    * @return List[Derivative] unique list of derivatives
    */
  def createDerivativesFromPaths(doc: DoclibDoc, paths: List[String]): List[ParentChildMapping] = {
    val consumerNameOption = Try(config.getString("consumer.name")).toOption
    paths.map(d => ParentChildMapping(_id = UUID.randomUUID(), childPath = d, parent = doc._id, consumer = consumerNameOption))
  }
}
