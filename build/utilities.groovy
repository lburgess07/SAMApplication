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
* compile_programs - compiles a list of programs (strings representing the program names, i.e. ["SAM1", "SAM2"])
*/
def compile_programs(String[] program_list){
	def tempOptions = "cyl space(5,5) unit(vio) new"
	def properties = BuildProperties.getInstance()

	program_list.each { program -> 
		//Initialize log file path
		String log_file = "${properties.workDir}/${program}_compile.log"

		//Set up Compilation 
		def compile = new MVSExec().pgm("IGYCRCTL").parm("LIST,MAP,NODYN")
		compile.dd(new DDStatement().name("TASKLIB").dsn(properties.SIGYCOMP).options("shr")) //compilerDS
		compile.dd(new DDStatement().name("SYSIN").dsn("${properties.srcPDS}($program)").options("shr"))
		compile.dd(new DDStatement().name("SYSLIB").dsn(properties.copyPDS).options("shr")) //copybook .COBCOPY
		compile.dd(new DDStatement().name("SYSLIN").dsn("${properties.objPDS}($program)").options("shr"))
		(1..17).toList().each { num ->
			compile.dd(new DDStatement().name("SYSUT$num").options(tempOptions))
			}
		compile.dd(new DDStatement().name("SYSMDECK").options(tempOptions))
		compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
		compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(log_file)))
		
		//Compile Program
		println("COMPILE: ${properties.srcPDS}:${program} -> ${properties.objPDS}:${program}")
		int rc = compile.execute() 

		if (rc > 4){
			println(" ${program} Compile failed!  RC=$rc")
			System.exit(rc)
		}
		else
			println(" ${program} Compile successful!  RC=$rc")
	}

	return(0)
}

/*
* link_programs - link edits a list of provided programs (strings representing the program names, i.e. ["SAM1", "SAM2"])
*/
def link_programs(String[] program_list){
	def tempOptions = "cyl space(5,5) unit(vio) new"
	def properties = BuildProperties.getInstance()
	
	program_list.each { program -> 
		//Initialize log file path
		String log_file = "${properties.workDir}/${program}_link.log"
		//Get appropriate link card
		link_card = get_linkCard(program)

		// Configure MVSExec() IEWL call to link-edit the program
		def link = new MVSExec().pgm("IEWL").parm("")
		link.dd(new DDStatement().name("SYSLMOD").dsn(properties.loadPDS).options("shr"))
		link.dd(new DDStatement().name("SYSUT1").options(tempOptions))
		link.dd(new DDStatement().name("OBJ").dsn(properties.objPDS).options("shr"))
		link.dd(new DDStatement().name("SYSLIN").instreamData(link_card)) 
		link.dd(new DDStatement().name("SYSLIB").dsn(properties.SCEELKED).options("shr")) //link lib
		link.dd(new DDStatement().dsn(properties.MACLIB).options("shr")) //SYS1.MACLIB
		link.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
		link.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(log_file)))
		println("LINK: ${properties.objPDS}:${program} -> ${properties.loadPDS}:${program}")

		// Execute the MVSExec() object
		int rc = link.execute()

		if (rc > 4){
			println(" ${program} Link failed!  RC=$rc")
			System.exit(rc)
		}
		else
			println(" ${program} Link successful!  RC=$rc")
	}
	return(0) 
}

/* Retreive Link Card */
def get_linkCard(String program){

sam1link   = """  
     INCLUDE OBJ(SAM1)
     ENTRY SAM1
     NAME SAM1(R)
"""
sam2link   = """  
     INCLUDE OBJ(SAM2)
     NAME SAM2(R)
"""
	switch(program){
		case "SAM1":
			return sam1link;
			break;
		case "SAM2":
			return sam2link;
			break;
		default:
			println("Unable to retrieve appropraite link card for program.")
			System.exit(1)
			break;
	}

}


// ***** Build Definitions: ******* //
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
	cli.d(longOpt:'delete', 'Flag indicating whether to delete previous datasets')
	cli.C(longOpt:'clean', 'Flag indicating whether to clean DBB collection')
	//cli.r(longOpt:'repo', args:1, argName:'url', 'DBB repository URL')
	//cli.i(longOpt:'id', args:1, argName:'id', 'DBB repository id')
	//cli.p(longOpt:'pw', args:1, argName:'password', 'DBB password')
	//cli.P(longOpt:'pwFile', args:1, argName:'file', 'Absolute or relative (from sourceDir) path to file containing DBB password')
	//cli.e(longOpt:'logEncoding', args:1, argName:'encoding', 'Encoding of output logs. Default is EBCDIC')
    //cli.b(longOpt:'buildHash', args:1, argName:'hash', 'Git commit hash for the build')


	def opts = cli.parse(cliArgs)
	if (opts.help) { // if help option used, print usage and exit
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
	if (opts.w) properties.workDir = opts.w //log file
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
		assert opts.s : 'Missing argument -s (--sourceDir)'
	}
	if (!opts.w) {
		assert opts.w : 'Missing argument -w (--workDir)'
	}
	if (!opts.q) {
		assert opts.q : 'Missing argument -q (--hlq)'
	}
}