@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
import com.ibm.jzos.FileFactory
import com.ibm.jzos.ZFile

def createDatasets(def datasets_map) {
    datasets_name = datasets_map.keySet(); //get array of datasets to create
	println "** Creating datasets: ${datasets_name}"
	if (datasets_name) {
		datasets_name.each { dataset ->
            options = datasets_map.get(dataset) //pull corresponding dataset options from map using $dataset as key
			new CreatePDS().dataset(dataset.trim()).options(options.trim()).create()
		}
	}
}



def deleteDatasets(String[] datasets){
	datasets.each { dataset ->
		if (ZFile.dsExists("//'$dataset'")){
			println("Removing ${dataset}. . .")
			ZFile.remove("//'$dataset'")
			}
	}
}

def copySeq(def copy_map){ // map format should be "full path to file" : "dataset name"
	files_name = copy_map.keySet();
	println "** Copying files: ${files_name}"
	if (files_name) {
		files_name.each { file -> 
			dataset = copy_map.get(file) //pull corresponding dataset from map
			dataset_format = "//'${dataset}'"
			println("Copying ${file} to ${dataset}");

			//execute 'cp' command:
			String cmd = "cp -v ${file} ${dataset_format}"
			StringBuffer response = new StringBuffer()
			StringBuffer error = new StringBuffer()
			
			Process process = cmd.execute()
			process.waitForProcessOutput(response, error)
			if (error) {
				println("*? Warning executing ls. command: $cmd \nerror: $error")	
			}
			else if (response) {
				println("Copy success.")
				//println(response)
			} 
		}
	}
}