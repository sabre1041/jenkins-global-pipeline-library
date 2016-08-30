package com.rhc

import groovy.json.JsonSlurper

class OpenShiftClient implements Serializable {
	
	CommandExecutor commandExecutor = new CommandExecutor()
	long maxMillisecondsToWait = 300000  
	long waitIntervalInMilliseconds = 1000 

	void login( String host ){
		String command = "oc login ${host} --insecure-skip-tls-verify=true --username=joe --password=redhat"
		commandExecutor.executeInJenkinsOrGroovy( command )
	}

	CommandOutput startBuild( String appName, String projectName ){
		String command = "oc start-build ${appName} -n ${projectName}"
		CommandOutput output = commandExecutor.executeInJenkinsOrGroovy( command )
		return output
	}

	CommandOutput getWhoAmI(){
		String command = "oc whoami"
		CommandOutput output = commandExecutor.executeInJenkinsOrGroovy( command )
		return output
	}

	CommandOutput getUserToken(){
		String command = "oc whoami -t"
		CommandOutput output = commandExecutor.executeInJenkinsOrGroovy( command )
		return output
	}

	String getTrimmedUserToken(){
		CommandOutput tokenOutput = getUserToken()
		String tokenStream = tokenOutput.getStandardOut()
		String tokenString = tokenStream.toString()
		String tokenTrimmedString = tokenString.trim()
		return tokenTrimmedString
	}

	void waitUntilBuildIsComplete( String buildId, String projectName ){

		long timeAlreadySpentWaiting = 0
		String trimmedBuildId = buildId.trim()
		while ( true ){
			String status = getBuildStatus( trimmedBuildId, projectName )
			println( "Build ${trimmedBuildId} is ${status}")
			if ( status.equalsIgnoreCase( 'Complete' ) ){
				break
			} else if ( status.equalsIgnoreCase( 'Failed' )  ) {
				throw new Exception( "Build ${trimmedBuildId} Failed! See OpenShift for logs" )
			} else {
				if ( timeAlreadySpentWaiting == maxMillisecondsToWait){
					throw new Exception( "Max interval to poll exceeded - ${maxMillisecondsToWait}" )
				} else {
					timeAlreadySpentWaiting += waitIntervalInMilliseconds
					Thread.sleep( waitIntervalInMilliseconds )
				}
			}
		}
	}


	String startBuildAndWaitUntilComplete( String appName, String projectName ){
		CommandOutput buildName = startBuild( appName, projectName )
		waitUntilBuildIsComplete( buildName.standardOut, projectName )
		return buildName.standardOut.trim()
	}

	String getBuildStatus( String buildId, String projectName ){
		CommandOutput output = commandExecutor.executeInJenkinsOrGroovy( "oc get builds ${buildId} -n ${projectName} -o json" )
		assert output.standardOut != null
		assert !output.standardOut.empty
		assert output.standardErr == null || output.standardErr.empty
		def result = new JsonSlurper().parseText( output.standardOut )
		return result.status.phase
	}
}
