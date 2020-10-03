@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
import groovy.time.*

@Field def tools = loadScript(new File("utilities.groovy"))

//Print Info
println("java.version="+System.getProperty("java.runtime.version"))
println("java.home="+System.getProperty("java.home"))
println("user.dir="+System.getProperty("user.dir"))

// parse command line arguments and load build properties
def usage = "build.groovy [options] buildfile"
def opts = tools.parseArgs(args, usage)
tools.validateRequiredOpts(opts)
def properties = tools.loadProperties(opts)
//if (!properties.userBuild) 
     //If not single file build, validateRequiredProperties
	//tools.validateRequiredProperties(["dbb.RepositoryClient.url", "dbb.RepositoryClient.userId", "password", "collection"])

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
Map dataset_map =  ["${properties.srcPDS}":"${properties.options}", "${properties.objPDS}":"${properties.options}", "${properties.loadPDS}":"${properties.loadOptions}", "${properties.copyPDS}":"${properties.options}" ]
tools.createDatasets(dataset_map);

String[] buildList

// Begin Creating Buildlist based on provided build argument (-f = full build, -i = incremental build, -u = user / single file build)

if (properties.fullBuild) { //full build
     //Copy all files (provide map of relative file or directory paths, and the destination dataset names)
     Map copy_hash = ["${properties.sourceDir}/cobol/":"${properties.srcPDS}","${properties.sourceDir}/copybook/":"${properties.copyPDS}"]
     copy_files(copy_hash)

     // since full build, we will build all programs (SAM1 & SAM2) apart of the SAM application
     buildList = ["${properties.member1}","${properties.member2}"]
}
else if (properties.incrementalBuild) { //incremental build

}
else if (properties.userBuild){ //user build

}
else { //no build option provided, halt build
     println("**** No Build Option Provided, Exiting... *****")
     System.exit(1)
}

// Build the programs contained in the buildList
build(buildList)     
return 0;



// **** Method Definitions: ******** //
def build(String[] programs){

     if (programs){
          tools.compile_programs(programs)  //Compile list of provided programs
          tools.link_programs(programs)  //Link provided programs
     }
     else{
          println("No programs to build!")
          System.exit(1)
     }

    return(0) //compiled and linked successfully, return 0 

}

def copy_files(Map copy_files) {

     files = copy_files.keySet()

     if (files){
          files.each { file -> 
               def dataset = copy_files.get(file) //retrieve dataset value from hash map
               println("COPY: ${file} -> ${dataset}")
               def copy = new CopyToPDS().file(new File(file)).dataset(dataset)
               if (copy.execute()) {
                     println("An error occuried copying ${file}. ")
                     System.exit(1)
               }
          }
     }
}
