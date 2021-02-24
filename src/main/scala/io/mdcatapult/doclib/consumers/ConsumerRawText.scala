package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.RawTextHandler
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.{DoclibDoc, ParentChildMapping}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.util.admin.{Server => AdminServer}
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
import org.mongodb.scala.MongoCollection

/**
  * RabbitMQ Consumer to extract files to raw text
  */
object ConsumerRawText extends AbstractConsumer {

  override def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    AdminServer(config).start()

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.documents-collection"))
    implicit val derivativesCollection: MongoCollection[ParentChildMapping] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.derivative-collection"))

    /** initialise queues **/
    val downstream: Queue[PrefetchMsg] = queue("downstream.queue")
    val upstream: Queue[DoclibMsg] = queue("consumer.queue")
    val supervisor: Queue[SupervisorMsg] = queue("doclib.supervisor.queue")

    val readLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.read-limit"))
    val writeLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.write-limit"))

    upstream.subscribe(
      new RawTextHandler(downstream, supervisor, readLimiter, writeLimiter).handle,
      config.getInt("consumer.concurrency")
    )
  }
}
