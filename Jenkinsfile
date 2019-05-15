pipeline {
  agent any
  environment {
    DOCKER_HUB_REPOSITORY = "$DOCKER_HUB_OWNER"
    DOCKER_IMAGE_TAG = ''
    DOCKER_IMAGE_NAME = ''
    IMAGE_VERSION = 'dev'
    DOCKER_UNIQUE_PARAMETER = ''
  }
  stages {
    stage('Setup environment') {
      steps {
        script {
          pom = readMavenPom file: 'dhis-2/pom.xml'
          IMAGE_VERSION = pom.version.toLowerCase()
          echo "Image version: ${IMAGE_VERSION}"
          
          DOCKER_UNIQUE_PARAMETER = "${env.JOB_NAME}"
          echo "Docker parameter: ${DOCKER_UNIQUE_PARAMETER}"
          DOCKER_IMAGE_TAG = "${IMAGE_VERSION}-alpine"
            echo "Will tag image as ${DOCKER_IMAGE_TAG}"
            dir ("dhis-2/dhis-e2e-test") {
              sh "TAG=${DOCKER_IMAGE_TAG} docker-compose -p ${DOCKER_UNIQUE_PARAMETER} up -d --build"
            }
          
          DOCKER_IMAGE_NAME = "dhis2-core:${DOCKER_IMAGE_TAG}"
        }
         
      }
    }

    stage('Run api tests') {
      steps {
      dir("dhis-2/dhis-e2e-test") {
        sh "docker-compose -p ${DOCKER_UNIQUE_PARAMETER} -f docker-compose.e2e.yml up --build --exit-code-from e2e-test"
      }
      }
      
    }

    stage('Publish image') {
      steps {
        sh "docker tag ${DOCKER_IMAGE_NAME} ${DOCKER_HUB_REPOSITORY}/${DOCKER_IMAGE_NAME}"
        
        withDockerRegistry([ credentialsId: "docker-hub-credentials", url: "" ]) {
         sh "docker push ${DOCKER_HUB_REPOSITORY}/${DOCKER_IMAGE_NAME}"
        }
      }
       

    }

  }
}
