@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
import groovy.time.*

/*
This build.groovy script should clean and create the following datasets: 
SAMPLE.OBJ
SAMPLE.LOAD
SAMPLE.cobol (src)
SAMPLE.COBCOPY

Then it will copy source code & copybooks, and compile SAM1&2, and then link the executables.
All tools functions used in this script are definied in utilities.groovy
*/

@Field def tools = loadScript(new File("utilities.groovy"))

/*
hlq        = "BURGESS"
sourceDir  = "/u/burgess/dbb/SAMApplication" // set automatically
compilerDS = "IGY.V6R1M0.SIGYCOMP"
linklib    = "CEE.SCEELKED"


srcPDS = "${hlq}.SAMPLE.COBOL" // src dataset
objPDS = "${hlq}.SAMPLE.OBJ" // obj dataset
loadPDS = "${hlq}.SAMPLE.LOAD" //load dataset (will contain the executables) 
copyPDS = "${hlq}.SAMPLE.COBCOPY"
member1 = "SAM1"
member2 = "SAM2"
*/

// DS Names
def srcPDS = "${hlq}.SAMPLE.COBOL" // src dataset
def objPDS = "${hlq}.SAMPLE.OBJ" // obj dataset
def loadPDS = "${hlq}.SAMPLE.LOAD" //load dataset (will contain the executables) 
def copyPDS = "${hlq}.SAMPLE.COBCOPY"
def member1 = "SAM1"
def member2 = "SAM2"

// Log Files
String sam1_compile_log = "${sourceDir}/log/sam1_compile.log"
String sam2_compile_log = "${sourceDir}/log/sam2_compile.log"
String sam1_link_log    = "${sourceDir}/log/sam1_link.log"
String sam2_link_log    = "${sourceDir}/log/sam2_link.log"

// DS Options
def options = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def loadOptions = "cyl space(100,10) dsorg(PO) recfm(U) blksize(32720) dsntype(library) msg(1)"

//Print Info
println("java.version="+System.getProperty("java.runtime.version"))
println("java.home="+System.getProperty("java.home"))
println("user.dir="+System.getProperty("user.dir"))

// parse command line arguments and load build properties
def usage = "build.groovy [options] buildfile"
def opts = tools.parseArgs(args, usage)
tools.validateRequiredOpts(opts)
def properties = tools.loadProperties(opts)
if (!properties.userBuild) 
     //If not single file build, validateRequiredProperties
	//tools.validateRequiredProperties(["dbb.RepositoryClient.url", "dbb.RepositoryClient.userId", "password", "collection"])


println("** Build properties at startup:")
println(properties.list())


def startTime = new Date()
properties.startTime = startTime.format("yyyyMMdd.hhmmss.mmm")
println("** Build start at $properties.startTime")

// create workdir (if necessary)
new File(properties.workDir).mkdirs()
println("** Build output will be in $properties.workDir")


// clean up dataset (maybe I shouldn't be doing this? - ask Dan
String[] datasets_delete = ["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"]
tools.deleteDatasets(datasets_delete);

// create new datasets
Map dataset_map =  ["$srcPDS":"$options", "$objPDS":"$options", "$loadPDS":"$loadOptions", "$copyPDS":"$options" ]
tools.createDatasets(dataset_map);

def buildList
def incremental = false

if (opts.arguments()) {
     buildList = tools.getBuildList(opts.arguments())
}
//incremental build of full build
else {
     //get last successful build's buildHash



}

// scan all the files in the process list for dependency data (team build only)
if (!incremental) {
	if (!properties.userBuild && buildList.size() > 0) { 
		// create collection if needed
		def repositoryClient = tools.getDefaultRepositoryClient()
		if (!repositoryClient.collectionExists(properties.collection))
			repositoryClient.createCollection(properties.collection) 
			
		println("** Scan the build list to collect dependency data")
		def scanner = new DependencyScanner()
		def logicalFiles = [] as List<LogicalFile>
		
		buildList.each { file ->
			// ignore whitespace files
			if (file.isAllWhitespace())
				return // only applies to local function
			// scan file
			println("Scanning $file")
			def logicalFile = scanner.scan(file, properties.sourceDir)
			// add file to logical file list
			logicalFiles.add(logicalFile)
			
			if (logicalFiles.size() == 500) {
				println("** Storing ${logicalFiles.size()} logical files in repository collection '$properties.collection'")
				repositoryClient.saveLogicalFiles(properties.collection, logicalFiles);
				println(repositoryClient.getLastStatus())  
				logicalFiles.clear() 		
			}
		}

		println("** Storing remaining ${logicalFiles.size()} logical files in repository collection '$properties.collection'")
		repositoryClient.saveLogicalFiles(properties.collection, logicalFiles);
		println(repositoryClient.getLastStatus())
	}
}

// **** Method Definitions: ******** //
def full_build {

     /*
     // ******* CLEAN UP DATASETS ********* //
     String[] datasets_delete = ["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"]
     tools.deleteDatasets(datasets_delete);

     // ******* CREATE NEW DATASETS ********* //
     Map dataset_map =  ["$srcPDS":"$options", "$objPDS":"$options", "$loadPDS":"$loadOptions", "$copyPDS":"$options" ]
     tools.createDatasets(dataset_map);

     */

     // ******* COPY SOURCE & COPYBOOKS FROM zFS to MVS ******* //

     //Copy SAM1 & SAM2 over into srcPDS (will be seperate members)
     println("COPY: Cobol Source (HFS) -> ${srcPDS}")
     def copy = new CopyToPDS().file(new File("${sourceDir}/cobol/")).dataset(srcPDS)
     copy.execute()

     //Copy CUSTCOPY and TRANREC copybooks over into copyPDS
     println("COPY: ${sourceDir}/copybook/ -> ${copyPDS}")
     copy = new CopyToPDS().file(new File("${sourceDir}/copybook/")).dataset(copyPDS)
     copy.execute()

     // ********* COMPILATION *********** //
     tools.compileProgram(srcPDS, member2, compilerDS, copyPDS, objPDS, sam2_compile_log) //Compile SAM2
     tools.compileProgram(srcPDS, member1, compilerDS, copyPDS, objPDS, sam1_compile_log) //Compile SAM1

     // ********* LINK EDIT PROGRAM *********  //
     tools.linkProgram(loadPDS, member2, objPDS, linklib, sam2link, sam2_link_log) //Link SAM2
     tools.linkProgram(loadPDS, member1, objPDS, linklib, sam1link, sam1_link_log) //Link SAM1

}

def user_build {


}

def incremental_build {


}