def call(Map pipelineParams) {
    pipeline {
      environment {
        registry = pipelineParams.registry
        registryCredential = pipelineParams.registryCredential
        dockerImage = ''
        helmRepo = pipelineParams.helmRepo
        helmPackage = pipelineParams.basePackage
        serviceName = pipelineParams.serviceName
      }
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
        stage('Update Helm') {
          steps{
              sh '''
              helm upgrade --install $serviceName $helmRepo/$helmPackage --set image.repository=$registry --set image.tag=$BUILD_TAG --set fullnameOverride=$serviceName-$helmPackage
              '''
          }
        }
        stage('Remove Unused docker image') {
          steps{
            sh "docker rmi $registry:$BUILD_TAG"
          }
        }
      }
    }
}
