import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*


def createDatasets(def datasets_map) {
    datasets_name = datasets_map.keySet(); //get array of datasets to create

	if (datasets_name) {
		datasets_name.each { dataset ->
            options = datasets_map.get(dataset) //pull corresponding dataset options from map using $dataset as key
			new CreatePDS().dataset(dataset.trim()).options(options.trim()).create()
			println "** Creating / verifying build dataset ${dataset.trim()}"
		}
	}
}

