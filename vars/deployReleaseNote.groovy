def call(Map pipelineParams) {
    pipeline {
      environment {
        registry = "weburnit/hello-world"
        registryCredential = 'weburnit'
        dockerImage = ''
        helmReleaseNoteRegistry = 'weburnit/hello-world-release-notes'
        helmReleaseNote = 'release-notes'
        helmRepo = 'lancar-helm-museum'
        helmPackage = 'go-base'
      }
      agent any
      stages {
        stage('Cloning Git') {
          steps {
            git branch: 'main', url: 'https://github.com/weburnit/hello-world.git'
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
        stage('Building Release Notes image') {
          steps{
            script {
                def dockerfile = 'Docs.Dockerfile'
                def docsImage = docker.build("${helmReleaseNoteRegistry}:${env.BUILD_TAG}", "-f ${dockerfile} .")

                docker.withRegistry( '', registryCredential ) {
                    docsImage.push()
                }
            }
          }
        }
        stage('Remove Unused docker image') {
          steps{
            sh "docker rmi $registry:$BUILD_TAG"
          }
        }
        stage('Update Helm') {
          steps{
              sh '''
              helm upgrade --install ${SERVICE_NAME} $helmRepo/$helmPackage --set image.repository=$registry --set image.tag=$BUILD_TAG --set fullnameOverride=${SERVICE_NAME}-golang
              '''
          }
        }
        stage('Update Helm Release Note service') {
          steps{
              sh '''
              helm upgrade --install ${SERVICE_NAME}-release-notes $helmRepo/$helmReleaseNote --set image.repository=${helmReleaseNoteRegistry} --set image.tag=$BUILD_TAG --set fullnameOverride=${SERVICE_NAME}-release-notes
              '''
          }
        }
      }
    }
}
