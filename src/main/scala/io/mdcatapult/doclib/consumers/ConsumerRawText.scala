package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.doclib.handlers.RawTextHandler
import org.mongodb.scala.{Document, MongoCollection}

import scala.concurrent.ExecutionContextExecutor

/**
  * RabbitMQ Consumer to extract files to raw text
  */
object ConsumerRawText extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-raw-text")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** Initialise Mongo **/
  implicit val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[Document] = mongo.collection

  /** initialise queues **/
  val downstream: Queue[PrefetchMsg] = new Queue[PrefetchMsg](config.getString("downstream.queue"))
  val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(new RawTextHandler(downstream).handle, config.getInt("upstream.concurrent"))

}
