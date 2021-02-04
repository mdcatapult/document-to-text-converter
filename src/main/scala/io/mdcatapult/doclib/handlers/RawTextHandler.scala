package io.mdcatapult.doclib.handlers

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.flag.{FlagContext, MongoFlagStore}
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.metrics.Metrics.handlerCount
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibDocExtractor, Origin, ParentChildMapping}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.rawtext.extractors.RawText
import io.mdcatapult.util.models.Version
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.InsertManyResult

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RawTextHandler(prefetch: Sendable[PrefetchMsg], supervisor: Sendable[SupervisorMsg])
                    (implicit ex: ExecutionContext,
                     config: Config,
                     collection: MongoCollection[DoclibDoc],
                     derivativesCollection: MongoCollection[ParentChildMapping]
                    ) extends LazyLogging {

  private val docExtractor = DoclibDocExtractor()
  private val version = Version.fromConfig(config)
  private val flags = new MongoFlagStore(version, docExtractor, collection, nowUtc)

  /**
    * handler of raw text
    * @param msg IncomingMsg to process
    * @param key routing key from rabbitmq
    * @return
    */
  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] = {
    logger.info(f"RECEIVED: ${msg.id}")
    val flagContext: FlagContext = flags.findFlagContext(Some(config.getString("consumer.queue")))

    val rawTextProcess = for {
      doc <- OptionT(fetch(msg.id))
      if !docExtractor.isRunRecently(doc)
      started: UpdatedResult <- OptionT.liftF(flagContext.start(doc))
      // TODO - validate mimetype here??
      newFilePath <- OptionT(extractRawText(doc.source))
      persisted <- OptionT(persist(doc, newFilePath))
      _ <- OptionT(enqueue(newFilePath, doc))
      _ <- OptionT.liftF(flagContext.end(doc, noCheck = started.changesMade))
    } yield (newFilePath, persisted, doc)

    rawTextProcess.value.andThen {
      case Success(result) => result match {
        case Some(r) =>
          supervisor.send(SupervisorMsg(id = r._3._id.toHexString))
          logger.info(f"COMPLETE: ${msg.id} - converted to raw text - ${r._1}")
          incrementHandlerCount("success")
        case None =>
          logger.info(f"${msg.id} - no document found")
          incrementHandlerCount("error_no_document")
      }
      case Failure(e) =>
        logger.error("error during handle process", e)
        incrementHandlerCount("unknown_error")

        fetch(msg.id).onComplete {
          case Failure(e) => logger.error(s"error retrieving document", e)
          case Success(doclibDocOption) => doclibDocOption match {
            case Some(foundDoc) =>
              flagContext.error(foundDoc, noCheck = true).andThen {
                case Failure(e) => logger.error("error attempting error flag write", e)
              }
            case None =>
              val message = f"${msg.id} - no document found"
              logger.error(message, new Exception(message))
          }
        }
    }
  }

  private def incrementHandlerCount(labels: String*): Unit = {
    val labelsWithDefaults = Seq(config.getString("consumer.name"), config.getString("consumer.queue")) ++ labels
    handlerCount.labels(labelsWithDefaults: _*).inc()
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

  def persist(doc: DoclibDoc, newFilePath: String): Future[Option[InsertManyResult]] =
    derivativesCollection.insertMany(createDerivativesFromPaths(doc, List(newFilePath))).toFutureOption()

  def extractRawText(source: String): Future[Option[String]] =
    Try(new RawText(source).extract) match {
      case Success(r) => Future.successful(Some(r))
      case Failure(e) => throw e
    }

  def fetch(id: String): Future[Option[DoclibDoc]] =
    collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

  /**
    * Create list of parent child mappings
    * @param doc DoclibDoc
    * @param paths List[String]
    * @return List[Derivative] unique list of derivatives
    */
  def createDerivativesFromPaths(doc: DoclibDoc, paths: List[String]): List[ParentChildMapping] = {
    val consumerNameOption = Try(config.getString("consumer.name")).toOption
    paths.map(d => ParentChildMapping(_id = UUID.randomUUID(), childPath = d, parent = doc._id, consumer = consumerNameOption))
  }
}
