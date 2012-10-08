import java.io.{File, FileInputStream}
import scala.collection.immutable.HashMap

class MemoryFileCache(path: String) {
  
  private val currentCache = this.recursiveLoad("/", new File(path), new HashMap[String, Array[Byte]])
  
  private def recursiveLoad(root: String, directory: File, inCache: HashMap[String, Array[Byte]]) : HashMap[String, Array[Byte]] = {
	var cacheBuilder = inCache  
    val contents = directory.listFiles()
	  
    // Recurse into folders
	contents.filter(_.isDirectory).foreach(file => cacheBuilder ++ this.recursiveLoad(root + file.getName + "/", file, cacheBuilder))
	  
	// Load each file
	contents.filter(!_.isDirectory).foreach(file => {
	  val filePath = file.getAbsolutePath()
	    
	  val source = scala.io.Source.fromFile(filePath)
	  val fileContents = source.map(_.toByte).toArray
	  source.close()
		
	  val fileName = root + file.getName
	  println(fileName)
	    
	  cacheBuilder += fileName -> fileContents
	})
	  
	cacheBuilder
  }
  
  def get(path : String) = this.currentCache.get(path)
  
  //def cache = { this.currentCache }

}