def call(Map pipelineParams) {
    def registry = pipelineParams.registry
    def registryCredential = pipelineParams.registryCredential
    def dockerImage = ''
    def helmRepo = pipelineParams.helmRepo
    def helmPackage = pipelineParams.basePackage
    def serviceName = pipelineParams.serviceName
    def helmReleaseNote = 'release-notes'
    def releaseNotes = pipelineParams.withReleaseNotes

    echo pipelineParams.withReleaseNotes
    echo releaseNotes
    pipeline {
      agent any
      stages {
        stage('Cloning Git') {
          steps {
            git branch: pipelineParams.branch, url: pipelineParams.git
          }
        }
        stage('Building image') {
          steps{
            script {
              dockerImage = docker.build registry + ":$BUILD_TAG"
            }
          }
        }
        stage('Deploy Image') {
          steps{
            script {
              docker.withRegistry( '', registryCredential ) {
                dockerImage.push()
              }
            }
          }
        }
        stage('Check Release Notes condition') {
          steps{
            script {
                if (releaseNotes.length() > 100) {
                    def dockerfile = 'Docs.Dockerfile'
                    def docsImage = docker.build("${registry}-release-notes:${env.BUILD_TAG}", "-f ${dockerfile} .")

                    docker.withRegistry( '', registryCredential ) {
                        docsImage.push()
                    }

                    echo "$releaseNotes"
                    sh '''
                    helm upgrade --install ${serviceName}-release-notes ${helmRepo}/${helmReleaseNote} --set image.repository=${registry}-release-notes --set image.tag=$BUILD_TAG --set fullnameOverride=${serviceName}-release-notes
                    '''

                    sh "docker rmi ${registry}-release-notes:${env.BUILD_TAG}"
                } else {
                    sh "echo 'Release notes is not enough to release'"
                }
            }
          }
        }
        stage('Update Helm') {
          steps{
            def registry = pipelineParams.registry
            def helmRepo = pipelineParams.helmRepo
            def helmPackage = pipelineParams.basePackage
            def serviceName = pipelineParams.serviceName
              sh '''
              helm upgrade --install ${serviceName} ${helmRepo}/${helmPackage} --set image.repository=${registry} --set image.tag=$BUILD_TAG --set fullnameOverride=${serviceName}-${helmPackage}
              '''
          }
        }
        stage('Remove Unused docker image') {
          steps{
            sh "docker rmi ${registry}:$BUILD_TAG"
          }
        }
      }
    }
}
