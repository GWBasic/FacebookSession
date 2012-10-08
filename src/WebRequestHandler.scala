import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler

class WebRequestHandler(memoryFileCache: MemoryFileCache) extends AbstractHandler {
	def handle(target: String,
                       baseRequest: Request,
                       request: HttpServletRequest,
                       response: HttpServletResponse) {
	  
		val requestedFile = memoryFileCache.get(target)
	  
		if (requestedFile.isDefined) {
			response.setContentType("text/html;charset=utf-8")
			response.setStatus(HttpServletResponse.SC_OK)
			baseRequest.setHandled(true)
			response.getOutputStream().write(requestedFile.get)
		} else if (target == "/loginviafacebook") {
			FacebookSessionCreator.startFacebookLogin(baseRequest, request, response)
		} else if (target == "/finishfacebooklogin") {
			FacebookSessionCreator.finishFacebookLogin(baseRequest, request, response)
		} else if (target == "/dosession") {
			SessionManager.doSession(baseRequest, request)
		/*} else {
			response.setContentType("text/html;charset=utf-8")
			response.setStatus(HttpServletResponse.SC_OK)
			baseRequest.setHandled(true)
			response.getWriter().println("<h1>" + target + "</h1>")*/
		}
	}
}