package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.PrefetchOrigin
import io.mdcatapult.rawtext.extractors.RawText
import io.mdcatapult.klein.queue.Queue
import org.bson.types.ObjectId
import org.mongodb.scala.{Document, MongoCollection}
import org.mongodb.scala.bson.{BsonBoolean, BsonDocument, BsonNull, BsonValue}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class RawTextHandler(downstream: Queue[PrefetchMsg])(implicit ac: ActorSystem, ex: ExecutionContextExecutor, config: Config, collection: MongoCollection[Document]) extends LazyLogging {

  def enqueue(newFilePath: String, doc: Document): Future[Option[Boolean]] = {
    downstream.send(PrefetchMsg(
      source = newFilePath,
      origin = Some(List(PrefetchOrigin(
        scheme = "mongodb",
        metadata = Some(Map[String, Any](
          "db" → config.getString("mongo.database"),
          "collection" → config.getString("mongo.collection"),
          "_id" → doc.getObjectId("_id").toString))))),
      tags = Some(doc.getList("tags", classOf[String]).asScala.toList ::: List("rawtext")),
      metadata = Some(doc.getOrElse("metadata", new BsonDocument()).asDocument()),
      derivative = Some(true)
    ))
    Future.successful(Some(true))
  }


  def persist(doc: Document, newFilePath: String): Future[Option[UpdateResult]] = {
    val id = doc.getObjectId("_id")
    val query = equal("_id", id)
    collection.updateOne(query, and(
      addToSet(config.getString("rawtext.targetProperty"), newFilePath),
      set(config.getString("doclib.flag"), true)
    )).toFutureOption()
  }


  def extractRawText(source: String): Future[Option[String]] =
    Try(new RawText(source).extract) match {
      case Success(r) => Future.successful(Some(r))
      case Failure(e) ⇒ throw e
    }


  def fetch(id: String): Future[Option[Document]] =
    collection.find(equal("_id", new ObjectId(id))).first().toFutureOption()

  /**
    * set processing flags on read document
    * @param id String
    * @param v BsonValue (null/boolean
    * @return
    */
  def setFlag(id: String, v: BsonValue): Future[Option[UpdateResult]] = {
    collection.updateOne(
      equal("_id", new ObjectId(id)),
      set(config.getString("doclib.flag"), v)
    ).toFutureOption()
  }


  def handle(msg: DoclibMsg, key: String): Future[Option[Any]] =
    (for {
      doc ← OptionT(fetch(msg.id))
      if doc.contains("source")
      _ ← OptionT(setFlag(msg.id, BsonNull()))
      newFilePath ← OptionT(extractRawText(doc.getString("source")))
      persisted ← OptionT(persist(doc, newFilePath))
      _ ← OptionT(enqueue(newFilePath, doc))
    } yield (newFilePath, persisted)).value.andThen({
      case Success(result) ⇒ result match {
        case Some(r) ⇒
          logger.info(f"COMPLETE: ${msg.id} - converted to raw text - ${r._1}")
        case None ⇒ setFlag(msg.id, BsonBoolean(false)).andThen({
          case Failure(err) ⇒ throw err
          case _ ⇒ logger.debug(f"DROPPING MESSAGE: ${msg.id}")
        })
      }
      case Failure(err) ⇒ throw err

    })

}
