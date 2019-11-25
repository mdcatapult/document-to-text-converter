package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.doclib.models.{Derivative, DoclibDoc, Origin}
import io.mdcatapult.doclib.models.metadata.{MetaString, MetaValueUntyped}
import io.mdcatapult.doclib.util.DoclibFlags
import io.mdcatapult.rawtext.extractors.RawText
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}


class RawTextHandler(prefetch: Sendable[PrefetchMsg], supervisor: Sendable[SupervisorMsg])
                    (implicit ac: ActorSystem,
                     ex: ExecutionContextExecutor,
                     config: Config,
                     collection: MongoCollection[DoclibDoc]
                    ) extends LazyLogging {

  lazy val flags = new DoclibFlags(config.getString("doclib.flag"))

  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] = {
    logger.info(f"RECEIVED: ${msg.id}")
    (for {
      doc ← OptionT(fetch(msg.id))
      started: UpdateResult ← OptionT(flags.start(doc))
      // TODO - validate mimetype here??
      newFilePath ← OptionT(extractRawText(doc.source))
      persisted ← OptionT(persist(doc, newFilePath))
      _ ← OptionT(enqueue(newFilePath, doc))
      _ ← OptionT(flags.end(doc, started.getModifiedCount > 0))
    } yield (newFilePath, persisted, doc)).value.andThen({

      case Success(result) ⇒ result match {
        case Some(r) ⇒ {
          supervisor.send(SupervisorMsg(id = r._3._id.toHexString))
          logger.info(f"COMPLETE: ${msg.id} - converted to raw text - ${r._1}")
        }
        case None ⇒ logger.info(f"${msg.id} - no document found")
      }
      case Failure(err) ⇒ OptionT(fetch(msg.id)).value.andThen({
        case Success(result) ⇒ result match {
          case Some(foundDoc) ⇒ flags.error(foundDoc, noCheck = true)
          case None => //Do nothing. The error is bubbling up. There is no mongo doc to set flags on
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


  def persist(doc: DoclibDoc, newFilePath: String): Future[Option[UpdateResult]] = {
    collection.updateOne(equal("_id", doc._id),
      addToSet("derivatives", Derivative("rawtext", newFilePath)),
    ).toFutureOption()
  }

  def extractRawText(source: String): Future[Option[String]] =
    Try(new RawText(source).extract) match {
      case Success(r) => Future.successful(Some(r))
      case Failure(e) ⇒ throw e
    }

  def fetch(id: String): Future[Option[DoclibDoc]] =
    collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

}
