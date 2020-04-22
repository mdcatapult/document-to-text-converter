package io.mdcatapult.doclib.handlers

import java.util.UUID

import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibDocExtractor, Origin, ParentChildMapping}
import io.mdcatapult.doclib.util.DoclibFlags
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.rawtext.extractors.RawText
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{Completed, MongoCollection}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RawTextHandler(prefetch: Sendable[PrefetchMsg], supervisor: Sendable[SupervisorMsg])
                    (implicit ex: ExecutionContext,
                     config: Config,
                     collection: MongoCollection[DoclibDoc],
                     derviativesCollection: MongoCollection[ParentChildMapping]
                    ) extends LazyLogging {

  private val docExtractor = DoclibDocExtractor()

  private val flagKey = config.getString("doclib.flag")

  private lazy val flags = new DoclibFlags(flagKey)

  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] = {
    logger.info(f"RECEIVED: ${msg.id}")
    (for {
      doc <- OptionT(fetch(msg.id))
      if !docExtractor.isRunRecently(doc)
      started: UpdateResult <- OptionT(flags.start(doc))
      // TODO - validate mimetype here??
      newFilePath <- OptionT(extractRawText(doc.source))
      persisted <- OptionT(persist(doc, newFilePath))
      _ <- OptionT(enqueue(newFilePath, doc))
      _ <- OptionT(flags.end(doc, noCheck = started.getModifiedCount > 0))
    } yield (newFilePath, persisted, doc)).value.andThen({

      case Success(result) => result match {
        case Some(r) =>
          supervisor.send(SupervisorMsg(id = r._3._id.toHexString))
          logger.info(f"COMPLETE: ${msg.id} - converted to raw text - ${r._1}")
        case None => logger.info(f"${msg.id} - no document found")
      }
      case Failure(_) => OptionT(fetch(msg.id)).value.andThen({
        case Success(result) => result match {
          case Some(foundDoc) => flags.error(foundDoc, noCheck = true)
          case None => () //Do nothing. The error is bubbling up. There is no mongo doc to set flags on
        }
      })
    })
  }

  def enqueue(newFilePath: String, doc: DoclibDoc): Future[Option[Boolean]] = {
    // Let prefetch know that it is an rawtext derivative
    val derivativeMetadata = List[MetaValueUntyped](MetaString("derivative.type", "rawtext"))
    prefetch.send(PrefetchMsg(
      source = newFilePath,
      origin = Some(List(Origin(
        scheme = "mongodb",
        metadata = Some(List(
          MetaString("db", config.getString("mongo.database")),
          MetaString("collection", config.getString("mongo.collection")),
          MetaString("_id", doc._id.toHexString),
        )),
      ))),
      tags = doc.tags,
      metadata = Some(doc.metadata.getOrElse(Nil) ::: derivativeMetadata),
      derivative = Some(true)
    ))
    Future.successful(Some(true))
  }

  def persist(doc: DoclibDoc, newFilePath: String): Future[Option[Completed]] = {
    derviativesCollection.insertMany(createDerivativesFromPaths(doc, List(newFilePath))).toFutureOption()
  }

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
  def createDerivativesFromPaths(doc: DoclibDoc, paths: List[String]): List[ParentChildMapping] =
  //TODO This same pattern is used in other consumers so maybe we can move to a shared lib in common or a shared consumer lib.
    paths.map(d => ParentChildMapping(_id = UUID.randomUUID, childPath = d, parent = doc._id, consumer = Some("rawtext_conversion")))

}
