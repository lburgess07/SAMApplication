@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
import com.ibm.jzos.FileFactory
import com.ibm.jzos.ZFile

/*
* createDatasets - creates datasets accepting map parameter with format of DATASET_NAME:DATASET_OPTIONS
*/
def createDatasets(Map datasets_map) {
    datasets_name = datasets_map.keySet(); //get array of datasets to create
	println "** Creating datasets: ${datasets_name}"
	if (datasets_name) {
		datasets_name.each { dataset ->
            options = datasets_map.get(dataset) //pull corresponding dataset options from map using $dataset as key
			new CreatePDS().dataset(dataset.trim()).options(options.trim()).create()
		}
	}
}


/*
* deleteDatasets - deletes one or more datasets, accepts an array of dataset name strings to delete
				   Checks if a provided dataset exists, and if so, deletes it.
*/
def deleteDatasets(def datasets){
	datasets.each { dataset ->
		if (ZFile.dsExists("//'$dataset'")){
			println("Removing ${dataset}. . .")
			ZFile.remove("//'$dataset'")
			}
	}
}

/*
* copySeq - copies one or more USS files to seq MVS datasets, accepts map with format USS_FILE_PATH:DATASET_NAME
*/
def copySeq(Map copy_map){ // map format should be "full path to file" : "dataset name"
	files_name = copy_map.keySet();
	println "** Copying files: ${files_name}"
	if (files_name) {
		files_name.each { file -> 
			dataset = copy_map.get(file) //pull corresponding dataset from map
			dataset_format = "//'${dataset}'" //adding '//' signifies destination is a MVS dataset
			println("Copying ${file} to ${dataset}");

			//initialize the copy command using USS 'cp' command
			String cmd = "cp -v ${file} ${dataset_format}"
			StringBuffer response = new StringBuffer()
			StringBuffer error = new StringBuffer()
			
			//execute command and process output
			Process process = cmd.execute()
			process.waitForProcessOutput(response, error)
			if (error) {
				println("*? Warning executing 'cp' shell command.\n command: $cmd \nerror: $error")	
			}
			else if (response) {
				println("Copy success.")
				//println(response)
			} 
		}
	}
}


def printFile(File file){
	println(file.text)
}
