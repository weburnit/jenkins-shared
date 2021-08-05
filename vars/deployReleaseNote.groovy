def call(Map pipelineParams) {
    pipeline {
      environment {
        registry = pipelineParams.registry
        registryCredential = pipelineParams.registryCredential
        helmRepo = pipelineParams.helmRepo
        serviceName = pipelineParams.serviceName
        helmReleaseNote = 'release-notes'
      }
      agent any
      stages {
        stage('Cloning Git') {
          steps {
            git branch: 'main', url: 'https://github.com/weburnit/hello-world.git'
          }
        }
        stage('Building Release Notes image') {
          steps{
            script {
                def dockerfile = 'Docs.Dockerfile'
                def docsImage = docker.build("${registry}-release-notes:${env.BUILD_TAG}", "-f ${dockerfile} .")

                docker.withRegistry( '', registryCredential ) {
                    docsImage.push()
                }
            }
          }
        }
        stage('Update Helm Release Note service') {
          steps{
              sh '''
              helm upgrade --install ${SERVICE_NAME}-release-notes $helmRepo/$helmReleaseNote --set image.repository=${registry}-release-notes --set image.tag=$BUILD_TAG --set fullnameOverride=${serviceName}-release-notes
              '''
          }
        }
        stage('Remove Unused docker image') {
          steps{
            sh "docker rmi ${registry}-release-notes:${env.BUILD_TAG}
          }
        }
      }
    }
}
