@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.* 
import com.ibm.jzos.FileFactory
import com.ibm.jzos.ZFile

// ***** METHOD DEFINITIONS ***** //

/*
* createDatasets - creates datasets accepting map parameter with format of DATASET_NAME:DATASET_OPTIONS
*/
def createDatasets(Map datasets_map) {
    datasets_name = datasets_map.keySet(); //get array of datasets to create
	println "CREATE: ${datasets_name}"
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
			println("DELETE: ${dataset}. . .")
			ZFile.remove("//'$dataset'")
			}
	}
}

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

/*
* compileProgram - compiles a program 
*/
def compileProgram(String srcDS, String member, String compilerDS, String copyDS, String objectDS, String log_file){
	def tempOptions = "cyl space(5,5) unit(vio) new"

	def compile = new MVSExec().pgm("IGYCRCTL").parm("LIST,MAP,NODYN")
	compile.dd(new DDStatement().name("TASKLIB").dsn("${compilerDS}").options("shr"))
	compile.dd(new DDStatement().name("SYSIN").dsn("${srcDS}($member)").options("shr"))
	compile.dd(new DDStatement().name("SYSLIB").dsn("${copyDS}").options("shr")) //copybook .COBCOPY
	compile.dd(new DDStatement().name("SYSLIN").dsn("${objectDS}($member)").options("shr"))
	(1..17).toList().each { num ->
		compile.dd(new DDStatement().name("SYSUT$num").options(tempOptions))
		}
	compile.dd(new DDStatement().name("SYSMDECK").options(tempOptions))
	compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
	compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(log_file)))
	println("COMPILE: ${srcDS}:${member} -> ${objectDS}:${member}")
	def rc = compile.execute()

	if (rc > 4){
		println(" ${member} Compile failed!  RC=$rc")
		System.exit(rc)
	}
	else
		println(" ${member} Compile successful!  RC=$rc")

	return(rc)
}

/*
* linkProgram - links a program
*/
def linkProgram(String loadDS, String member, String objectDS, String linklibDS, String link_card, String log_file){
	def tempOptions = "cyl space(5,5) unit(vio) new"

	def link = new MVSExec().pgm("IEWL").parm("")
	link.dd(new DDStatement().name("SYSLMOD").dsn(loadDS).options("shr"))
	link.dd(new DDStatement().name("SYSUT1").options(tempOptions))
	link.dd(new DDStatement().name("OBJ").dsn(objectDS).options("shr"))
	link.dd(new DDStatement().name("SYSLIN").instreamData(link_card)) 
	link.dd(new DDStatement().name("SYSLIB").dsn(linklibDS).options("shr"))
	link.dd(new DDStatement().dsn("SYS1.MACLIB").options("shr")) 
	link.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
	link.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(log_file)))
	println("LINK: ${objectDS}:${member} -> ${loadDS}:${member}")
	def rc = link.execute()

	if (rc > 4){
		println(" ${member} Link failed!  RC=$rc")
		System.exit(rc)
	}
	else
		println(" ${member} Link successful!  RC=$rc")

	return(rc)

}


// ***** Incremental Build Definitions: ******* //
def parseArgs(String[] cliArgs, String usage) {
	def cli = new CliBuilder(usage: usage)
	cli.s(longOpt:'sourceDir', args:1, argName:'dir', 'Absolute path to source directory')
	cli.w(longOpt:'workDir', args:1, argName:'dir', 'Absolute path to the build output directory')
	cli.q(longOpt:'hlq', args:1, argName:'hlq', 'High level qualifier for partition data sets')
	cli.c(longOpt:'collection', args:1, argName:'name', 'Name of the dependency data collection')
	cli.u(longOpt:'userBuild', 'Flag indicating running a user build')
	cli.h(longOpt:'help', 'Prints this message')
    cli.f(longOpt:'fullBuild', 'Flag indicating running a full build')
    cli.u(longOpt:'userBuild', 'Flag indicating running a user (single-file) build') //need to identify copybook
    cli.i(longOpt:'incrementalBuild', 'Flag indicating running an incremental/impact build')
    //cli.t(longOpt:'team', args:1, argName:'hlq', 'Team build hlq for user build syslib concatenations')
	//cli.r(longOpt:'repo', args:1, argName:'url', 'DBB repository URL')
	//cli.i(longOpt:'id', args:1, argName:'id', 'DBB repository id')
	//cli.p(longOpt:'pw', args:1, argName:'password', 'DBB password')
	//cli.P(longOpt:'pwFile', args:1, argName:'file', 'Absolute or relative (from sourceDir) path to file containing DBB password')
	//cli.e(longOpt:'logEncoding', args:1, argName:'encoding', 'Encoding of output logs. Default is EBCDIC')
     //cli.b(longOpt:'buildHash', args:1, argName:'hash', 'Git commit hash for the build')


	def opts = cli.parse(cliArgs)
	if (opts.h) { // if help option used, print usage and exit
	 	cli.usage()
		System.exit(0)
	}

    return opts
}


def loadProperties(OptionAccessor opts) {
	// check to see if there is a ./build.properties to load
	def properties = BuildProperties.getInstance()
	// One may also use YAML files as an alternative to properties files (DBB 1.0.6 and later):
	//     def buildPropFile = new File("${getScriptDir()}/build.yaml")
	def buildPropFile = new File("${getScriptDir()}/build.properties")
	if (buildPropFile.exists())
   		BuildProperties.load(buildPropFile)

	// set command line arguments
	if (opts.s) properties.sourceDir = opts.s
	if (opts.w) properties.workDir = opts.w
	if (opts.b) properties.buildHash = opts.b
	if (opts.q) properties.hlq = opts.q
	if (opts.c) properties.collection = opts.c
	if (opts.t) properties.team = opts.t
	if (opts.e) properties.logEncoding = opts.e
	if (opts.E) properties.errPrefix = opts.E

	if (opts.u) properties.userBuild = "true"
	if (opts.f) properties.fullBuild = "true"
	if (opts.f) properties.incrementalBuild = "true"

	
	// override new default properties
	if (opts.r) properties.'dbb.RepositoryClient.url' = opts.r
	if (opts.i) properties.'dbb.RepositoryClient.userId' = opts.i
	if (opts.p) properties.'dbb.RepositoryClient.password' = opts.p
	if (opts.P) properties.'dbb.RepositoryClient.passwordFile' = opts.P
	
	/*// handle --clean option
	if (opts.C)  {
		println("** Clean up option selected")
		def repo = getDefaultRepositoryClient()
		
		println("* Deleting dependency collection ${properties.collection}")
		repo.deleteCollection(properties.collection)

		println("* Deleting build result group ${properties.collection}Build")
		repo.deleteBuildResults("${properties.collection}Build")
		
		System.exit(0)
	} */
	
	/*// add build hash if not specific
	if (!opts.b && !properties.userBuild) {
		properties.buildHash = getCurrentGitHash() as String
	} */

	// load datasets.properties containing system specific PDS names used by Mortgage Application build
	properties.load(new File("${getScriptDir()}/datasets.properties"))
	// load file.properties containing file specific properties like script mappings and CICS/DB2 content flags
	// One may also use YAML files as an alternative to properties files (DBB 1.0.6 and later):
	//     properties.load(new File("${getScriptDir()}/file.yaml"))
	//properties.load(new File("${getScriptDir()}/file.properties"))
	// load bind.properties containing DB2 BIND PACKAGE parameters used by Mortgage Application build
	//properties.load(new File("${getScriptDir()}/bind.properties"))
	// load bindlinkEditScanner.properties containing Link Edit scanning options used by Mortgage Application build
	//properties.load(new File("${getScriptDir()}/linkEditScanner.properties"))

	return properties
}


def validateRequiredOpts(OptionAccessor opts) {
	if (!opts.s) {
		assert opts.s : 'Missing argument --sourceDir'
	}
	if (!opts.w) {
		assert opts.w : 'Missing argument --workDir'
	}
	if (!opts.q) {
		assert opts.q : 'Missing argument --hlq'
	}
}

def getBuildList(List<String> args) {
    def properties = BuildProperties.getInstance()
    def files = []

	// Set the buildFile or buildList property
	if (args) {
		def buildFile = args[0]
	    if (buildFile.endsWith(".txt")) {
			if (buildFile.startsWith("/"))
				properties.buildListFile = buildFile
		      else
				properties.buildListFile = "$properties.sourceDir/$buildFile".toString()
	  	}
	    else {
			properties.buildFile = buildFile
	    }
	}

	// check to see if a build file was passed in
	if (properties.buildFile) {
		println("** Building file $properties.buildFile")
		files = [properties.buildFile]
	}
	// else check to see if a build list file was passed in
	else if (properties.buildListFile) {
		println("** Building files listed in $properties.buildListFile")
	    files = new File(properties.buildListFile) as List<String>
	}
	
	return files
}

