import org.eclipse.jetty.server.Server;

object Program {
	def main(args: Array[String]) {
	  
	  val memoryFileCache = new MemoryFileCache("webcontent");
	  
      val server = new Server(8080)
      server.setHandler(new WebRequestHandler(memoryFileCache))
 
      server.start()
      server.join()
    }
}