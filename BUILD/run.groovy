@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 

@Field def runUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS.SAMPLE"
sourceDir  = "/u/burgess/dbb/SAMApplication"

def loadPDS     = "${hlq}.LOAD"
def custFile    = "${hlq}.CUSTFILE"
def tranFile    = "${hlq}.TRANFILE"
def custOut     = "${hlq}.CUSTOUT"
def custRpt     = "${hlq}.CUSTRPT"

def tranFileOptions = "tracks space(100,10) lrecl(80) dsorg(PS) recfm(F,B) blksize(32720) new"
def custFileOptions = "tracks space(100,10) dsorg(PS) recfm(V,B) lrecl(600) blksize(604) new"
def tempOptions     = "cyl space(5,5) unit(vio) new"

// Clean up / delete previous datasets
String[] datasets_delete = ["$custFile", "$tranFile"]
runUtils.deleteDatasets(datasets_delete)

// Create SAMPLE.TRANFILE , SAMPLE.CUSTFILE, SAMPLE.CUSTRPT, SAMPLE.CUSTOUT with appropriate options
Map dataset_map = ["$tranFile":"$tranFileOptions", "$custFile":"$custFileOptions"]
runUtils.createDatasets(dataset_map)

// Copy sample customer file and transaction file
Map copy_map = ["${sourceDir}/RESOURCES/custfile.txt":"${custFile}", "${sourceDir}/RESOURCES/tranfile.txt":"$tranFile"];
runUtils.copySeq(copy_map)

// ****** RUN SAM 1 ******* //

// Initialize log files / output:
File sys_output = new File("${sourceDir}/LOG/sysout.txt")
def custOut_path = "${sourceDir}/LOG/custout.txt"
def custRpt_path = "${sourceDir}/LOG/custrpt.txt"

def run_jcl = "${sourceDir}/BUILD/run.jcl" //JCL file
def sysprint_file = "${sourceDir}/LOG/sysprint.out"

// Execute JCL from file on HFS
JCLExec jclExec = new JCLExec()
println("** Executing JCL **")
jclExec.file(new File(run_jcl)).execute()

// Get Job data
def maxRC = jclExec.getMaxRC()
def jobID = jclExec.getSubmittedJobId()
def jobName = jclExec.getSubmittedJobName()

if (maxRC == "CC 0000")
    printf("** Execution Success. ** \n RC: ${maxRC} \n jobID: ${jobID} \n jobName: ${jobName} \n")
else
    printf("** Execution Failure. ** \n RC: ${maxRC} \n jobID: ${jobID} \n jobName: ${jobName} \n")

// Save JCL Exec SYSOUT to sys_output file
jclExec.saveOutput('SYSOUT', sys_output)

// Copy output Datasetsto HFS:
copy_map = ["${custOut_path}":"${custOut}", "${custRpt_path}": "${custRpt}"]
runUtils.copySeqtoHFS(copy_map)

printf("\n** ${custRpt} or ${custRpt_path} **\n")
runUtils.printFile(new File(custRpt_path))