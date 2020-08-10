@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 

@Field def buildUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS.SAMPLE"
sourceDir  = "/u/burgess/dbb/SAMApplication"

def loadPDS = "${hlq}.LOAD"
def custFile = "${hlq}.CUSTFILE"
def tranFile = "${hlq}.TRANFILE"
def custOut = "${hlq}.CUSTOUT"
def custRpt = "${hlq}.CUSTRPT"

def options = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def custOutOptions = "cyl space(10,10) unit(sysda) dsorg(PS) recfm(V,B) lrecl(600) blksize(604) dsntype(library) new"
def custRptOptions = "cyl space(10,10) unit(sysda) dsorg(PS) recfm(F,B) lrecl(133) blksize(0) dsntype(library) new"
def tempOptions = "cyl space(5,5) unit(vio) new"

// Clean up / delete leftover datasets (SAMPLE.CUSTRPT, SAMPLE.CUSTOUT)

//Create SAMPLE.TRANFILE , SAMPLE.CUSTFILE, SAMPLE.CUSTRPT, SAMPLE.CUSTOUT with appropriate options
def dataset_map = ["$tranFile":"$options","$custFile":"$custOutOptions", "$custOut":"$custOutOptions", "$custRpt":"$custRptOptions"]
buildUtils.createDatasets(dataset_map);

//Copy over SAMPLE.TRANFILE, SAMPLE.CUSTFILE

println("Copying SAMPLE.TRANFILE from zFS to MVS . . .")
def copy = new CopyToPDS().file(new File("${sourceDir}/RESOURCES/tranfile.txt")).dataset(tranFile).member("TRAN")
copy.execute()

System.exit(0)

println("Copying SAMPLE.CUSTFILE from zFS to MVS . . .")
copy = new CopyToPDS().file(new File("${sourceDir}/RESOURCES/custfile.txt")).dataset(custFile).member("CUSTFILE")
copy.execute()

// ****** RUN SAM 1 ******* //

def run = new MVSExec().pgm("SAM1").parm("")
//
//// add DD statements to the MVSExec command
run.dd(new DDStatement().name("TASKLIB").dsn(loadPDS).options("shr"))
run.dd(new DDStatement().name("SYSOUT").options(tempOptions))
run.dd(new DDStatement().name("SYSUDUMP").options(tempOptions))
run.dd(new DDStatement().name("CUSTFILE").dsn(custFile).options(tempOptions)) //what about Options?
run.dd(new DDStatement().name("TRANFILE").dsn(tranFile).options(tempOptions))
run.dd(new DDStatement().name("CUSTOUT").dsn(custOut).options(custOutOptions))
run.dd(new DDStatement().name("CUSTRPT").dsn(custRpt).options(custRptOptions))
def rc = run.execute()

if (rc > 4){
    println("Program execution failed!  RC=$rc")
    System.exit(rc)
}
else
    println("Program execution successful!  RC=$rc")


//alocate execute copy free


/*
run.dd(new DDStatement().name("SYSPRINT").options(tempOptions))// the important stuff
File file = File.createTempFile("temp",".tmp")
run.copy(new CopyToHFS().ddName("SYSOUT").file(file)) */
