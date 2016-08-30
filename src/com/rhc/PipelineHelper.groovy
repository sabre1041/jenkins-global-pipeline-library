package com.rhc


def buildApplication( config ){
    if ( config.appRootContext ){
        dir ( config.appRootContext ){ 
            executeBuildCommands(config)
        }
    } else {
        executeBuildCommands(config)
    }
}

def buildAndDeployImage( config ){
    if ( config.buildImageCommands == null ){
        echo 'No buildImageCommands, using default OpenShift image build and deploy'
        startDefaultOpenShiftBuildAndDeploy( config )
    } else {
        echo 'Found buildImageCommands, executing in shell'
        executeListOfShellCommands( config.buildImageCommands, config.appName, config.envs[0].projectName )
    }
}

def getBuildTools(){
    return ['node-0.10', 'Maven3.3.9'];
}


String getLastNameFromJenkinsJobName(){
	String jobName = "${env.JOB_NAME}"
	String[] tokens = jobName.split( '-' )
	String developerLastName = tokens[ tokens.length -1 ]
	return developerLastName.toLowerCase()
}
def startDefaultOpenShiftBuildAndDeploy( config ){

	
	
	if ( config.containsKey( 'projectName') && config.containsKey( 'envs') ){
		throw new Exception( "Both projectName and envs cannot be defined! Choose projectName for Dev Pipeline and envs for Release Pipeline." )
	}
	
	OpenShiftClient oc = new OpenShiftClient()
	if ( config.projectName != null ){
		String lastName =  getLastNameFromJenkinsJobName()
		
		oc.startBuildAndWaitUntilComplete( config.appName, "${config.projectName}-${lastName}" )
	} else if ( config.envs != null ) {
		oc.startBuildAndWaitUntilComplete( config.appName, config.envs[0].projectName )
	} else {
		throw new Exception( "Something went wrong with your config" )
	}
	
}

def executeBuildCommands( config ){
    def tools = getBuildTools()

    if ( config.buildTool == null ){
        echo 'No build tool declared. Any commands will execute directly in the shell.'
        executeListOfShellCommands( config.buildCommands, config.appName, config.envs[0].projectName )
    } else if ( config.buildTool in tools ) {
        echo "Using build tool: ${config.buildTool}"
        executeListOfToolCommands( config.buildCommands, config.buildTool )
    } else {
        error "${config.buildTool} is currently unsupported. Please select one of ${tools}."
    }
}

def executeListOfShellCommands( commandList, appName, projectName ){
    for (int i=0; i<commandList.size(); i++){
        def command = commandList[i]
        sh "${command} ${appName} ${projectName}"
    }
}

def executeListOfToolCommands( commandList, buildTool ){
    def toolHome = tool "${buildTool}"
    for (int i=0; i<commandList.size(); i++){
        def command = commandList[i]
        sh "${toolHome}/bin/${command}" 
    }
}

def promoteImage( currentEnv, newEnv, config ){
    def dockerClient = new DockerClient()

    def currentImageRepositoryWithVersion = dockerClient.buildRepositoryStringWithVersion( config.dockerRegistry, currentEnv.projectName, config.appName, 'latest' )
    def newImageRepositoryWithVersion = dockerClient.buildRepositoryStringWithVersion( config.dockerRegistry, newEnv.projectName, config.appName, 'latest' )
    
    return dockerClient.promoteImageBetweenRepositories( currentImageRepositoryWithVersion, newImageRepositoryWithVersion )
}

def login( config ){
    def oc = new OpenShiftClient()
    oc.login( config.ocHost )
    def docker = new DockerClient()
    docker.login( config.dockerRegistry, oc.getTrimmedUserToken() )
}
