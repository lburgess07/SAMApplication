import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*

// Clean up leftover datasets (SAMPLE.CUSTRPT, SAMPLE.CUSTOUT)

//Create SAMPLE.TRANFILE , SAMPLE.CUSTFILE


// ------------------
// Run the program
// define the MVSExec command to link the file
def run = new MVSExec().pgm("HELLO").parm("")
//
//// add DD statements to the MVSExec command
run.dd(new DDStatement().name("TASKLIB").dsn("${loadPDS}($member)").options("shr"))
run.dd(new DDStatement().name("SYSOUT").options(tempOptions))
run.dd(new DDStatement().name("SYSPRINT").options(tempOptions))// the important stuff
File file = File.createTempFile("temp",".tmp")
run.copy(new CopyToHFS().ddName("SYSOUT").file(file))
rc = run.execute()

if (rc > 4){
    println("Program execution failed!  RC=$rc")
    System.exit(rc)
}
else
    println("Program execution successful!  RC=$rc")


println(file.text) //print temp file (contains output) to the terminal 

//alocate execute copy free