package io.mdcatapult.doclib.handlers

import io.mdcatapult.doclib.consumer.HandlerResult
import io.mdcatapult.doclib.models.DoclibDoc

/**
 * Convenience class for queues that you subscribe to for sending but don't care about the result
 * @param doclibDoc
 */
case class AnyHandlerResult(doclibDoc: DoclibDoc) extends HandlerResult
