@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 

@Field def runUtils= loadScript(new File("utilities.groovy"))

hlq        = "BURGESS"
sourceDir  = "/u/burgess/dbb/SAMApplication"

def loadPDS     = "${hlq}.SAMPLE.LOAD"
def custFile    = "${hlq}.SAMPLE.CUSTFILE"
def tranFile    = "${hlq}.SAMPLE.TRANFILE"
def custOut     = "${hlq}.SAMPLE.CUSTOUT"
def custRpt     = "${hlq}.SAMPLE.CUSTRPT"
// Options (for dataset creation)
def tranFileOptions = "tracks space(100,10) lrecl(80) dsorg(PS) recfm(F,B) blksize(32720) new"
def custFileOptions = "tracks space(100,10) dsorg(PS) recfm(V,B) lrecl(600) blksize(604) new"
def tempOptions     = "cyl space(5,5) unit(vio) new"
// Log Files:
File sys_output = new File("${sourceDir}/log/sysout.out")
def custOut_path = "${sourceDir}/log/custout.out"
def custRpt_path = "${sourceDir}/log/custrpt.out"
def run_jcl = "${sourceDir}/build/run.jcl" //points to JCL file to run SAM1 on HFS
def sysprint_file = "${sourceDir}/log/sysprint.out"


// Clean up / delete previous datasets
String[] datasets_delete = ["$custFile", "$tranFile"]
runUtils.deleteDatasets(datasets_delete)

// Create SAMPLE.TRANFILE , SAMPLE.CUSTFILE, SAMPLE.CUSTRPT, SAMPLE.CUSTOUT with appropriate options
Map dataset_map = ["$tranFile":"$tranFileOptions", "$custFile":"$custFileOptions"]
runUtils.createDatasets(dataset_map)

// Copy sample customer file and transaction file
Map copy_map = ["${sourceDir}/resources/custfile.txt":"${custFile}", "${sourceDir}/resources/tranfile.txt":"$tranFile"];
copyHFStoSeq(copy_map)

// ****** RUN SAM 1 ******* //

// Submit JCL from file on HFS
JCLExec sam1 = new JCLExec()
println("** Executing JCL **")
sam1.file(new File(run_jcl)).execute()

/*
// Submit JCL from dataset member
JCLExec sam1 = new JCLExec()
sam1.dataset('USR1.JCL').member('TEST').execute()
*/

// Get Job data
def maxRC = sam1.getMaxRC()
def jobID = sam1.getSubmittedJobId()
def jobName = sam1.getSubmittedJobName()

// Save JCL Exec SYSOUT to sys_output file
sam1.saveOutput('SYSOUT', sys_output)

if (maxRC == "CC 0000")
    printf("** SUCCESS ** \n JobID: ${jobID} \n JobName: ${jobName} \n")
else {
    printf("** ERROR ** \n RC: ${maxRC} \n JobID: ${jobID} \n JobName: ${jobName} \n")
    System.exit(1)
}

// Copy output Datasets to HFS for displaying to console / log:
copy_map = ["${custOut_path}":"${custOut}", "${custRpt_path}": "${custRpt}"]
copySeqtoHFS(copy_map)

//Print custRpt to the console
printf("\n** ${custRpt} or ${custRpt_path} **\n")
println(new File(custRpt_path).text)




// **** METHOD DEFINITIONS ***** //

/*
* copySeq - copies one or more USS files to sequential datasets, accepts map with format USS_FILE_PATH:DATASET_NAME
*/
def copyHFStoSeq(Map copy_map){ // map format should be "full path to file" : "dataset name"
	files_name = copy_map.keySet();
	if (files_name) {
		files_name.each { file -> 
			dataset = copy_map.get(file) //pull corresponding dataset from map
			dataset_format = "//'${dataset}'" //adding '//' signifies destination is a MVS dataset
			println("COPY: ${file} -> ${dataset} (sequential). . .");

			//initialize the copy command using USS 'cp' command
			String cmd = "cp -v ${file} ${dataset_format}" //using -v to actually get a console response
			StringBuffer response = new StringBuffer()
			StringBuffer error = new StringBuffer()
			
			//execute command and process output
			Process process = cmd.execute()
			process.waitForProcessOutput(response, error)
			if (error) {
				println("*? Warning executing 'cp' shell command.\n command: $cmd \n error: $error")	
				return(1)
			}
			else if (response) {
				//println(response)
				return(0)
			} 
		}
	}
}

/*
* copySeqtoHFS - copies one or more sequential dataset to HFS files, accepts map with format USS_FILE_PATH:DATASET_NAME
*/
def copySeqtoHFS(Map copy_map){ // map format should be "full path to file" : "dataset name"
	files_name = copy_map.keySet();
	if (files_name) {
		files_name.each { file -> 
			dataset = copy_map.get(file) //pull corresponding dataset from map
			dataset_format = "//'${dataset}'" //adding '//' signifies destination is a MVS dataset
			println("COPY: ${dataset} (sequential) -> ${file}. . .");

			//initialize the copy command using USS 'cp' command
			String cmd = "cp -v ${dataset_format} ${file}" //using -v to actually get a console response
			StringBuffer response = new StringBuffer()
			StringBuffer error = new StringBuffer()
			
			//execute command and process output
			Process process = cmd.execute()
			process.waitForProcessOutput(response, error)
			if (error) {
				println("*? Warning executing 'cp' shell command.\n command: $cmd \n error: $error")
				return(1)	
			}
			else if (response) {
				return(0)
			} 
		}
	}
}