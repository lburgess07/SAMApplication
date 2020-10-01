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


// DS Names
def srcPDS = "${hlq}.SAMPLE.COBOL" // src dataset
def objPDS = "${hlq}.SAMPLE.OBJ" // obj dataset
def loadPDS = "${hlq}.SAMPLE.LOAD" //load dataset (will contain the executables) 
def copyPDS = "${hlq}.SAMPLE.COBCOPY"
def member1 = "SAM1"
def member2 = "SAM2"

// Log Files (not moved into build.properties)
String sam1_compile_log = "${sourceDir}/log/sam1_compile.log"
String sam2_compile_log = "${sourceDir}/log/sam2_compile.log"
String sam1_link_log    = "${sourceDir}/log/sam1_link.log"
String sam2_link_log    = "${sourceDir}/log/sam2_link.log"

*/

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

if (opts.C){
     // clean up all build datasets, delete any datasets that already exist
     String[] datasets_delete = ["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"]
     tools.deleteDatasets(datasets_delete);
}

// create new datasets
Map dataset_map =  ["$srcPDS":"$options", "$objPDS":"$options", "$loadPDS":"$loadOptions", "$copyPDS":"$options" ]
tools.createDatasets(dataset_map);

String[] buildList

if (opts.f) { //full build

     //Copy all files (provide map of relative file or directory paths, and the destination dataset names)
     Map copy_hash = ["cobol/":"${properties.srcPDS}","copybook/":"${properties.copyPDS}"]
     copy_files(copy_hash)

     // since full build, we will build all programs (SAM1 & SAM2) apart of the SAM application
     buildList = ["${properties.member1}","${properties.member2}"]


}
else if (opts.i) { //incremental build

}
else if (opts.u){ //user build


}
else { //no build option provided, halt build
     println("**** No Build Option Provided, Exiting... *****")
     System.exit(1)
}

// Build the programs contained in the buildList
rc = build(buildList)

if (rc){
     println("There was an error building programs.")
     System.exit(1)
}
else
     println("Build Completed")
     
return 0;



// **** Method Definitions: ******** //
def build(String[] programs){

     if (programs){
          
          //Compile list of provided programs
          def rc = tools.compile_programs(programs)

          if (rc){
               return(rc);
          }

          //Link provided programs
          rc = tools.link_programs(programs)

          if (rc)
               return(rc);
          
          return(0); //compiled and linked successfully, return 0
     }

     return 1; //no programs provided, return 1

}

def copy_files(Map copy_files) {

     files = copy_files.keySet()
     def sourceDir = properties.sourceDir

     if (files){
          files.each { file -> 
               def dataset = copy_files.get(file) //retrieve dataset value from hash map
              
               println("COPY: ${file} -> ${srcPDS}")
               def copy = new CopyToPDS().file(new File("${sourceDir}/${file}")).dataset(dataset)
               if (copy.execute()) {
                     println("An error occuried copying ${file}. ")
                     System.exit(1)
               }
          }
     }
}