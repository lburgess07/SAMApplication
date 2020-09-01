@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 

/*
This build.groovy script should clean and create the following datasets: 
SAMPLE.OBJ
SAMPLE.LOAD
SAMPLE.COBOL (src)
SAMPLE.COBCOPY

Then it will copy source code & copybooks, and compile SAM1&2, and then link the executables.
All buildUtils functions used in this script are definied in utilities.groovy
*/

@Field def buildUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS.SAMPLE"
sourceDir  = "/u/burgess/dbb/SAMApplication" // set automatically
compilerDS = "IGY.V6R1M0.SIGYCOMP"
linklib    = "CEE.SCEELKED"
sam1link   = """  
     INCLUDE OBJ(SAM1)
     ENTRY SAM1
     NAME SAM1(R)
"""
sam2link   = """  
     INCLUDE OBJ(SAM2)
     NAME SAM2(R)
"""

// DS Names
def srcPDS = "${hlq}.COBOL" // src dataset
def objPDS = "${hlq}.OBJ" // obj dataset
def loadPDS = "${hlq}.LOAD" //load dataset (will contain the executables) 
def copyPDS = "${hlq}.COBCOPY"
def member1 = "SAM1"
def member2 = "SAM2"

// Log Files
String sam1_compile_log = "${sourceDir}/LOG/sam1_compile.log"
String sam2_compile_log = "${sourceDir}/LOG/sam2_compile.log"
String sam1_link_log    = "${sourceDir}/LOG/sam1_link.log"
String sam2_link_log    = "${sourceDir}/LOG/sam2_link.log"
// DS Options
def options = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def loadOptions = "cyl space(100,10) dsorg(PO) recfm(U) blksize(32720) dsntype(library) msg(1)"

// ******* CLEAN UP DATASETS ********* //
String[] datasets_delete = ["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"]
buildUtils.deleteDatasets(datasets_delete);

// ******* CREATE NEW DATASETS ********* //
Map dataset_map =  ["$srcPDS":"$options", "$objPDS":"$options", "$loadPDS":"$loadOptions", "$copyPDS":"$options" ]
buildUtils.createDatasets(dataset_map);

// ******* COPY SOURCE & COPYBOOKS FROM zFS to MVS ******* //

//Copy SAM1 & SAM2 over into srcPDS (will be seperate members)
println("Copying cobol source files . . .")
def copy = new CopyToPDS().file(new File("${sourceDir}/COBOL/")).dataset(srcPDS)
copy.execute()

//Copy CUSTCOPY and TRANREC copybooks over into copyPDS
println("Bringing the copybooks over . . .")
copy = new CopyToPDS().file(new File("${sourceDir}/COPYBOOK/")).dataset(copyPDS)
copy.execute()

// ********* COMPILATION *********** //
buildUtils.compileProgram(srcPDS, member2, compilerDS, copyPDS, objPDS, sam2_compile_log) //Compile SAM2
buildUtils.compileProgram(srcPDS, member1, compilerDS, copyPDS, objPDS, sam1_compile_log) //Compile SAM1

// ********* LINK PROGRAM *********  //
buildUtils.linkProgram(loadPDS, member2, objPDS, linklib, sam2link, sam2_link_log) //Link SAM2
buildUtils.linkProgram(loadPDS, member1, objPDS, linklib, sam1link, sam1_link_log) //Link SAM1