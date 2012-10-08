import java.io.OutputStreamWriter
import java.net.{URLConnection, URL}
import java.nio.ByteBuffer
import java.util.{Date, Formatter}

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import scala.util.Random

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler

object FacebookSessionCreator {
    val random = new Random
    val keyBytes = new Array[Byte](16);
    random.nextBytes(keyBytes)
  
    val key = new SecretKeySpec(keyBytes, "AES");
  
	def startFacebookLogin(baseRequest: Request,
                       request: HttpServletRequest,
                       response: HttpServletResponse) {
	  
		val cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		
		// Get the date as a byte array
		val now = new Date().getTime
		val nowBytes = ByteBuffer.allocate(8).putLong(now).array();
		
		// Encrypt the current date
		val encryptedTimestamp = new Array[Byte](cipher.getOutputSize(nowBytes.length));
    	var encryptedTimestampLength = cipher.update(nowBytes, 0, nowBytes.length, encryptedTimestamp, 0);
    	encryptedTimestampLength += cipher.doFinal(encryptedTimestamp, encryptedTimestampLength);
    	
		val fmt = new Formatter();   
		encryptedTimestamp.foreach(b => fmt.format("%02X", b.asInstanceOf[java.lang.Object]))
    	val encryptedTimestampBase64 = fmt.toString()
	  
		response.sendRedirect("https://www.facebook.com/dialog/oauth?client_id=169657566400735&redirect_uri=http://localhost:8080/finishfacebooklogin&scope=&state=" + encryptedTimestampBase64)
	}

	def finishFacebookLogin(baseRequest: Request,
                       request: HttpServletRequest,
                       response: HttpServletResponse) {
		
		try {
			val parameters = request.getParameterMap()
			val encryptedTimestampBase64 = parameters.get("state")(0)
			val code = parameters.get("code")(0)
			
			val userAgent = request.getHeader("User-Agent")
		  
			val encryptedTimestamp = new Array[Byte](encryptedTimestampBase64.length / 2)
			for (ctr <- 0 until encryptedTimestamp.size) {
				encryptedTimestamp(ctr) = 
				  ((Character.digit(encryptedTimestampBase64.charAt(ctr * 2), 16) << 4).asInstanceOf[Byte]
                  + Character.digit(encryptedTimestampBase64.charAt((ctr * 2) + 1), 16)).asInstanceOf[Byte]
			}
		  
			val cipher = Cipher.getInstance("AES")
			cipher.init(Cipher.DECRYPT_MODE, key)
			
			val thenBytes = new Array[Byte](cipher.getOutputSize(encryptedTimestamp.length))
			val ptLength = cipher.update(encryptedTimestamp, 0, encryptedTimestamp.length, thenBytes, 0);
	    	cipher.doFinal(thenBytes, ptLength);
	    	
			val now = new Date().getTime
	    	val then = ByteBuffer.wrap(thenBytes).getLong();
			
			val delay = now - then;
		  
			// If the user took less then 90 seconds, log in
			if (delay > -5000 && delay < 90000) {
				// success
				println("Contacting facebook to verify that the user is who (s)he says (s)he is")
				
				val myRedirectUri = "http://localhost:8080/finishfacebooklogin&scope=&state=" + encryptedTimestampBase64
				val u = new URL(
				    "https://graph.facebook.com/oauth/access_token?client_id=169657566400735&redirect_uri="
				    + myRedirectUri
				    + "&client_secret=4aca92ffafec6685a4a18d465e6dfacb&code="
				    + code);
				val conn = u.openConnection()
				conn.setRequestProperty("User-Agent", "scala session")
				conn.setConnectTimeout(15000)

				conn.connect

				val resultString = scala.io.Source.fromInputStream(conn.getInputStream).mkString("")
				val result = resultString.split('&') map { str =>
					val pair = str.split('=')
						(pair(0) -> pair(1))
				} toMap
				
				// TODO: There's no error checking here; I'm assuming that there will be an exception at some point
				// if the user didn't authenticate correctly
				
				val token = result.get("access_token").get
				
				// Expires
				val expiresSecondsString = result.get("expires").get
				val expiresSeconds = expiresSecondsString.toInt
				val expires = new Date(new Date().getTime() + (expiresSeconds * 1000))
				
				println("Facebook session expires on: " + expires.toLocaleString())
				
				// Set up session
				val session = SessionManager.createNewSession(token, expires, userAgent)
				
				// The user should be directed to a magic URL with a session token in it
				response.sendRedirect("sessioncreated.html?sessionId=" + session.sessionId)
			} else {
				throw new Exception("invalid token")
			}
		} catch {
		  	case e: Exception =>
		  	  println("Error validating a facebook login: " + e.getMessage())
		  	  startFacebookLogin(baseRequest, request, response)
		}
	}
}