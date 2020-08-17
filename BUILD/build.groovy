@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
//groovy.transform allows you to use @Field

/*
This build.groovy script should clean and create the following datasets: 
SAMPLE.OBJ
SAMPLE.LOAD
SAMPLE.COBOL (src)
SAMPLE.COBCOPY

Then it will copy source code & copybooks, and compile SAM1&2, and then link the executables
*/

@Field def buildUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS.SAMPLE"
sourceDir  = "/u/burgess/dbb/SAMApplication"
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
def loadPDS = "${hlq}.LOAD" //load dataset (will contain the executable) 
def copyPDS = "${hlq}.COBCOPY"
def member1 = "SAM1"
def member2 = "SAM2"

// DS Options
def options = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def loadOptions = "cyl space(100,10) dsorg(PO) recfm(U) blksize(32720) dsntype(library) msg(1)"
def tempOptions = "cyl space(5,5) unit(vio) new"

// ******* CLEAN UP DATASETS ********* //
String[] datasets_delete = ["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"]
buildUtils.deleteDatasets(datasets_delete);

// ******* CREATE NEW DATASETS ********* //
def dataset_map =  ["$srcPDS":"$options", "$objPDS":"$options", "$loadPDS":"$loadOptions", "$copyPDS":"$options" ]
buildUtils.createDatasets(dataset_map);

/* ****** COPY SOURCE to appropriate DATASETS *******
 /COBOL/SAM1.cbl -> SAMPLE.COBOL(SAM1)
 /COBOL/SAM2.cbl -> SAMPLE.COBOL(SAM2)
*/

//Copy SAM1
println("Copying cobol source files . . .")
def copy = new CopyToPDS().file(new File("${sourceDir}/COBOL/")).dataset(srcPDS)
copy.execute()

//Need to copy the copybook over into COBCOPY? 
println("Bringing the copybooks over . . .")
copy = new CopyToPDS().file(new File("${sourceDir}/COPYBOOK/")).dataset(copyPDS)
copy.execute()

// ********* COMPILATION of SAM1 & SAM 2 ********** //

// Compile SAM 2
println("Compiling ${srcPDS} into member ${member2}. . .")
def compile = new MVSExec().pgm("IGYCRCTL").parm("LIST,MAP,NODYN")
compile.dd(new DDStatement().name("TASKLIB").dsn("${compilerDS}").options("shr"))
compile.dd(new DDStatement().name("SYSIN").dsn("${srcPDS}($member2)").options("shr"))
compile.dd(new DDStatement().name("SYSLIB").dsn("${copyPDS}").options("shr")) //copybook .COBCOPY
compile.dd(new DDStatement().name("SYSLIN").dsn("${objPDS}($member2)").options("shr"))
(1..17).toList().each { num ->
	compile.dd(new DDStatement().name("SYSUT$num").options(tempOptions))
	   }
compile.dd(new DDStatement().name("SYSMDECK").options(tempOptions))
compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${sourceDir}/LOG/sam2_compile.log")))
def rc = compile.execute()

if (rc > 4){
    println("SAM 2 Compile failed!  RC=$rc")
    System.exit(rc)
}
else
    println("SAM 2 Compile successful!  RC=$rc")


// Compile SAM 1
println("Compiling ${srcPDS} into member ${member1}. . .")
compile = new MVSExec().pgm("IGYCRCTL").parm("LIST,MAP,NODYN")
compile.dd(new DDStatement().name("TASKLIB").dsn("${compilerDS}").options("shr"))
compile.dd(new DDStatement().name("SYSIN").dsn("${srcPDS}($member1)").options("shr"))
compile.dd(new DDStatement().name("SYSLIB").dsn("${copyPDS}").options("shr"))
compile.dd(new DDStatement().name("SYSLIN").dsn("${objPDS}($member1)").options("shr"))
(1..17).toList().each { num ->
	compile.dd(new DDStatement().name("SYSUT$num").options(tempOptions))
	   }
compile.dd(new DDStatement().name("SYSMDECK").options(tempOptions))
compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${sourceDir}/LOG/sam1_compile.log")))
rc = compile.execute()

if (rc > 4){
    println("SAM 1 Compile failed!  RC=$rc")
    System.exit(rc)
}
else
    println("SAM 1 Compile successful!  RC=$rc")


// ********* LINK PROGRAM *********  //


//Link SAM2:
println("Linking SAM2. . .")	
def link = new MVSExec().pgm("IEWL").parm("")
link.dd(new DDStatement().name("SYSLMOD").dsn(loadPDS).options("shr"))
link.dd(new DDStatement().name("SYSUT1").options(tempOptions))
link.dd(new DDStatement().name("OBJ").dsn(objPDS).options("shr"))
link.dd(new DDStatement().name("SYSLIN").instreamData(sam2link)) 
link.dd(new DDStatement().name("SYSLIB").dsn(linklib).options("shr"))
link.dd(new DDStatement().dsn("SYS1.MACLIB").options("shr")) 
link.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
link.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${sourceDir}/LOG/sam2_link.log")))
rc = link.execute()

if (rc > 4){
    println("SAM 2 Link failed!  RC=$rc")
    System.exit(rc)
}
else
    println("SAM 2 Link successful!  RC=$rc")


//Link SAM1:
println("Linking SAM1. . .")	
link = new MVSExec().pgm("IEWL").parm("")
link.dd(new DDStatement().name("SYSLMOD").dsn(loadPDS).options("shr"))
link.dd(new DDStatement().name("SYSUT1").options(tempOptions))
link.dd(new DDStatement().name("OBJ").dsn(objPDS).options("shr"))
link.dd(new DDStatement().name("SYSLIN").instreamData(sam1link))
link.dd(new DDStatement().name("SYSLIB").dsn(linklib).options("shr"))
link.dd(new DDStatement().dsn("SYS1.MACLIB").options("shr")) 
link.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
link.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${sourceDir}/LOG/sam1_link.log")))
rc = link.execute()

if (rc > 4){
    println("SAM 1 Link failed!  RC=$rc")
    System.exit(rc)
}
else
    println("SAM 1 Link successful!  RC=$rc")

