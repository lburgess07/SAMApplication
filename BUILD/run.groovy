@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 

@Field def buildUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS.SAMPLE"
sourceDir  = "/u/burgess/dbb/SAMApplication"

def loadPDS     = "${hlq}.LOAD"
def custFile    = "${hlq}.CUSTFILE"
def tranFile    = "${hlq}.TRANFILE"
def custOut     = "${hlq}.CUSTOUT"
def custRpt     = "${hlq}.CUSTRPT"

def tranFileOptions = "tracks space(100,10) lrecl(80) dsorg(PS) recfm(F,B) blksize(32720) new"
def custFileOptions = "tracks space(100,10) dsorg(PS) recfm(V,B) lrecl(600) blksize(604) new"
def custOutOptions  = "tracks space(10,10) unit(SYSDA) dsorg(PS) recfm(V,B) lrecl(600) blksize(604) new"
def custRptOptions  = "tracks space(10,10) unit(SYSDA) dsorg(PS) recfm(F,B) lrecl(133) blksize(0) new"
def tempOptions     = "cyl space(5,5) unit(vio) new"

// Clean up / delete previous datasets
String[] datasets_delete = ["$custFile", "$tranFile", "$custOut", "$custRpt"]
buildUtils.deleteDatasets(datasets_delete);

// Create SAMPLE.TRANFILE , SAMPLE.CUSTFILE, SAMPLE.CUSTRPT, SAMPLE.CUSTOUT with appropriate options
def dataset_map = ["$tranFile":"$tranFileOptions","$custFile":"$custFileOptions", "$custOut":"$custOutOptions", "$custRpt":"$custRptOptions"]
buildUtils.createDatasets(dataset_map);

// Copy sample customer file and transaction file
def copy_map = ["${sourceDir}/RESOURCES/custfile.txt":"${custFile}", "${sourceDir}/RESOURCES/tranfile.txt":"$tranFile"];
buildUtils.copySeq(copy_map)

// ****** RUN SAM 1 ******* //

def run = new MVSExec().pgm("SAM1").parm("")
run.dd(new DDStatement().name("TASKLIB").dsn(loadPDS).options("shr"))
run.dd(new DDStatement().name("SYSOUT").options(tempOptions))
run.dd(new DDStatement().name("SYSUDUMP").options(tempOptions))
run.dd(new DDStatement().name("CUSTFILE").dsn(custFile).options("shr")) 
run.dd(new DDStatement().name("TRANFILE").dsn(tranFile).options("shr"))
run.dd(new DDStatement().name("CUSTOUT").dsn(custOut).options("shr"))
run.dd(new DDStatement().name("CUSTRPT").dsn(custRpt).options("shr"))
File sys_output = new File("${sourceDir}/LOG/SAM1_sysout.log")
File cust_out = new File("${sourceDir}/LOG/custout.txt")
File cust_rpt = new File("${sourceDir}/LOG/custrpt.txt")
File sys_dump = new File("${sourceDir}/LOG/SAM1_sysudump.log")
run.copy(new CopyToHFS().ddName("SYSOUT").file(sys_output))
run.copy(new CopyToHFS().ddName("CUSTOUT").file(cust_out))
run.copy(new CopyToHFS().ddName("CUSTRPT").file(cust_rpt))
run.copy(new CopyToHFS().ddName("SYSUDUMP").file(sys_dump))
println("** Running SAM1")
def rc = run.execute()

if (rc > 4){
    println("Program execution failed!  RC=$rc \n")
    System.exit(rc)
}
else
    println("Program execution successful!  RC=$rc \n")

println("** CUSTRPT DATASET OUTPUT **\n")
println(cust_rpt.text)
//println("** CUSTOUT DATASET OUTPUT **\n")
//println(cust_out.text)
println("** PROGRAM SYSTEM OUTPUT **\n")
println(sys_output.text)