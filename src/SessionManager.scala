import java.io.OutputStreamWriter
import java.net.{URLConnection, URL}
import java.nio.ByteBuffer
import java.util.{Date, Formatter}

import scala.actors._
import scala.collection.mutable.{HashMap, HashSet, MutableList}
import scala.util.Random

import Actor._

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler

object SessionManager {
	def random = new Random
	
	val sessionsById = new HashMap[String, Session]
	val sessionsByUser = new HashMap[String, HashSet[Session]] 

	private val fiber = actor {
		loop {
			react {
			  case task:Function0[_] => task()
			}
		}
	}
  
	def createNewSession(token: String, expires: Date, userAgent: String) = {
		val sessionIdBytes = new Array[Byte](50);
		random.nextBytes(sessionIdBytes)
		
		val fmt = new Formatter();   
		sessionIdBytes.foreach(b => fmt.format("%02X", b.asInstanceOf[java.lang.Object]))
    	val sessionId = fmt.toString

		val session = new Session(sessionId, token, expires, userAgent)
		
		fiber ! (() => {
			sessionsById += session.sessionId -> session
			val userSessions = sessionsByUser.getOrElseUpdate(session.facebookUserId, HashSet[Session]())
			println(userSessions.size)
			userSessions.add(session)
			
			// TODO: Need to remove dead sessions
		})
		
		println("New session: " + session.sessionId)
		
		session
	}
	
	def remove(session: Session) {
		fiber ! (() => {
		  	sessionsById.remove(session.sessionId)
			val userSessions = sessionsByUser.get(session.facebookUserId).get
			userSessions.remove(session)
	  	})
	}
	
	def doSession(baseRequest: Request,
                       request: HttpServletRequest) {
	  
		val parameters = request.getParameterMap()
		val sessionId = parameters.get("sessionId")(0)
		val highestId = parameters.get("highestId")(0).toInt
		
		val continuation = ContinuationSupport.getContinuation(request);
		continuation.suspend
		
		fiber ! (() => {
		  val sessionOption = sessionsById.get(sessionId)
		  
		  if (sessionOption.isEmpty) {
		    // error
		    val response = continuation.getServletResponse().asInstanceOf[HttpServletResponse];
			response.setContentType("text/html;charset=utf-8")
			response.setStatus(HttpServletResponse.SC_NOT_FOUND)
			baseRequest.setHandled(true)
			response.getWriter().println("session id is invalid: " + sessionId)

			continuation.complete
		  } else {
		    // success
		    continuation.setAttribute("baseRequest", baseRequest)
		    continuation.setAttribute("highestId", highestId)
		    sessionOption.get.handleRequest(continuation)
		  }
		})
	}
}