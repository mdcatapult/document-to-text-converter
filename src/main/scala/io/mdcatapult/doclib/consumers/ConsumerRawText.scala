package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.RawTextHandler
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala.MongoCollection

import scala.concurrent.ExecutionContextExecutor

/**
  * RabbitMQ Consumer to extract files to raw text
  */
object ConsumerRawText extends AbstractConsumer("consumer-unarchive") {

  def start()(implicit as: ActorSystem, materializer: ActorMaterializer, mongo: Mongo): SubscriptionRef = {
    implicit val ex: ExecutionContextExecutor = as.dispatcher
    implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))

    /** initialise queues **/
    val downstream: Queue[PrefetchMsg] = new Queue[PrefetchMsg](config.getString("downstream.queue"), Some("rawtext"))
    val upstream: Queue[DoclibMsg] = new Queue[DoclibMsg](config.getString("upstream.queue"), Some("rawtext"))
    val supervisor: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("doclib.supervisor.queue"), Some("rawtext"))
    upstream.subscribe(new RawTextHandler(downstream).handle, config.getInt("upstream.concurrent"))
  }
}
