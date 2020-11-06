package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.ConsumerName
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.RawTextHandler
import io.mdcatapult.doclib.messages._
import io.mdcatapult.doclib.models.{DoclibDoc, ParentChildMapping}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.{Envelope, Queue}
import io.mdcatapult.util.admin.{Server => AdminServer}
import org.mongodb.scala.MongoCollection
import play.api.libs.json.Format

/**
  * RabbitMQ Consumer to extract files to raw text
  */
object ConsumerRawText extends AbstractConsumer(ConsumerName) {

  override def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    AdminServer(config).start()

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.database.getCollection(config.getString("mongo.collection"))
    implicit val derivativesCollection: MongoCollection[ParentChildMapping] =
      mongo.database.getCollection(config.getString("mongo.derivative_collection"))

    def queue[T <: Envelope](property: String)(implicit f: Format[T]): Queue[T] =
      new Queue[T](config.getString(property), consumerName = Some("rawtext"))

    /** initialise queues **/
    val downstream: Queue[PrefetchMsg] = queue("downstream.queue")
    val upstream: Queue[DoclibMsg] = queue("upstream.queue")
    val supervisor: Queue[SupervisorMsg] = queue("doclib.supervisor.queue")

    upstream.subscribe(
      new RawTextHandler(downstream, supervisor).handle,
      config.getInt("upstream.concurrent")
    )
  }
}
