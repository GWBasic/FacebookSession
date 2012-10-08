import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger
import java.net.{URLConnection, URL}
import java.nio.ByteBuffer
import java.util.{Date, Formatter}

import scala.actors._
import scala.collection.immutable.List
import scala.collection.immutable.Map
import scala.collection.mutable.Queue
import scala.util.Random
import scala.util.parsing.json._

import Actor._

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler

class Session(val sessionId: String, val token: String, val expires: Date, val userAgent: String) {
  
	// {"id":"796384767","name":"Andrew Rondeau","first_name":"Andrew","last_name":"Rondeau","link":"http:\/\/www.facebook.com\/GWBasic","username":"GWBasic","hometown":{"id":"104051369632373","name":"Shrewsbury, Massachusetts"},"location":{"id":"104022926303756","name":"Palo Alto, California"},"quotes":"\"I Just believe in me\"  (John Lennon)","sports":[{"id":"103780659661402","name":"Skiing"}],"inspirational_people":[{"id":"12503781721","name":"Joseph Campbell"},{"id":"8706934962","name":"Timothy Leary"},{"id":"135388936479828","name":"John Lennon"},{"id":"109530739066143","name":"Thomas Edison"},{"id":"103711193001118","name":"Hunter S. Thompson"}],"gender":"male","timezone":-7,"locale":"en_US","verified":true,"updated_time":"2012-09-08T18:23:19+0000"}
	private val resultObject = {
		val u = new URL("https://graph.facebook.com/me?access_token=" + token)
		val conn = u.openConnection()
		conn.setRequestProperty("User-Agent", "scala session")
		conn.setConnectTimeout(15000)
	
		conn.connect
	
		val resultString = scala.io.Source.fromInputStream(conn.getInputStream).mkString("")
		JSON.parseFull(resultString).get.asInstanceOf[Map[String, Any]]
	}
	
	val facebookUserName = resultObject.get("name").get.toString
	val facebookUserId = resultObject.get("id").get.toString
	
	// TODO: Use friends API to see who's logged in!
	// https://graph.facebook.com/me/friends?access_token=
  
	private var sessionRunning = true
	
	private class QueuedMessage(val messageId: Int, val message: Any) { }
	
	private class MessageToQueue(val message: Any) { }
  
	private val messageSimulator = actor {
		val random = new Random()
	  
		loopWhile (sessionRunning) {
			reactWithin (random.nextInt(5000)) {
			  	case _ => {
			  		println("Sending a simulated message to session: " + sessionId)
			  		sendMessage(new Date().getTime)
			  	}
			}
		}
	}

	private val fiber = actor {
		var nextMessageId: Int = 0
	  	var idleContinuation: Continuation = null
	  	var lastConnection = new Date().getTime
	  	var loopDelay = Integer.MAX_VALUE
	  	val messageQueue = new Queue[QueuedMessage]()
	    
	  	def returnResults(newContinuation: Continuation) {
			if (null != idleContinuation) {
				val response = idleContinuation.getServletResponse().asInstanceOf[HttpServletResponse];
				response.setContentType("text/html;charset=utf-8")
				response.setStatus(HttpServletResponse.SC_ACCEPTED)
				
				val baseRequest = idleContinuation.getAttribute("baseRequest").asInstanceOf[Request]
				baseRequest.setHandled(true)
				
				val itemsToSend = for (message <- messageQueue.toList) yield new JSONObject(Map("id" -> message.messageId, "message" -> message.message)) 
				val objectToSend = new JSONArray(itemsToSend)
				
				response.getWriter().println(objectToSend);
			
				idleContinuation.complete
			}
			
		    if (null != newContinuation) {
		    	lastConnection = new Date().getTime
		    } else if (null == idleContinuation && lastConnection + 10000 < new Date().getTime) {
		    	SessionManager.remove(this)
		    	sessionRunning = false
		    	
		    	println("Session lost: " + sessionId)
		    }
	
		    idleContinuation = newContinuation
				
			loopDelay = Integer.MAX_VALUE
	  	}

		loopWhile (sessionRunning) {
			reactWithin(loopDelay) {
				//case task:Function0[_] => task()
			  
			  	case message: MessageToQueue => {
			  	  
			  		messageQueue.enqueue(new QueuedMessage(nextMessageId, message.message))
			  		nextMessageId += 1
			  	  
			  		// Give a short delay in case there are additional messages (Nagle delay)
			  		loopDelay = 20
			  	}
			  
				// If we get a continuation, end the old one and hold onto this one 
			  	case newContinuation: Continuation => {
			  	 
			  		// need to remove acked messages from the queue
			  		val highestId = newContinuation.getAttribute("highestId").asInstanceOf[Int]
			  		messageQueue.dequeueAll(message => message.messageId <= highestId)
			  	  
			  		returnResults(newContinuation)
			  	}

			  	// This is when any remaining messages are sent
			  	case TIMEOUT => returnResults(null)
			}
		}
	}

  	def handleRequest(continuation: Continuation) {
  		fiber ! continuation
  	}
  	
  	def sendMessage(message: Any) {
  		fiber ! new MessageToQueue(message)
  	}
}