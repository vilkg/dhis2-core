pipeline {
    agent {
        label 'master'
    }
    options {
        disableConcurrentBuilds()
    }

    environment {
        DHIS2_VERSION = ''
        DOCKER_HUB_REPOSITORY = "${DOCKER_HUB_OWNER}/core-dev"
        DOCKER_IMAGE_TAG = ''
        DOCKER_IMAGE_FULL_NAME = ''
        COMPOSE_PARAMETER = ''
        TESTS_IMAGE_FULL_NAME = ''
    }

    stages {
        stage('Prepare env for release deployment') {
            when{
                tag pattern: "^2.\\d+.*", comparator: "REGEXP"
            }
            steps {
                script {
                    DOCKER_HUB_REPOSITORY = "${DOCKER_HUB_OWNER}/core"
                    pom = readMavenPom file: 'dhis-2/pom.xml'
                    DHIS2_VERSION = pom.version.toLowerCase()

                    echo "DHIS2 version: ${DHIS2_VERSION}"

                    DOCKER_IMAGE_TAG = "${DHIS2_VERSION}"
                }
            }
        }

        stage('Prepare env for dev deployment') {
            when {
                not {
                    buildingTag()
                }
            }

            steps {
                script {
                    DOCKER_IMAGE_TAG = "${env.BRANCH_NAME}"
                }
            }
        }
        stage('Build image') {
            steps {
                script {
                    COMPOSE_PARAMETER = "${env.JOB_NAME}"
                    echo "Docker compose parameter: ${COMPOSE_PARAMETER}"

                    DOCKER_IMAGE_FULL_NAME = "${DOCKER_HUB_REPOSITORY}:${DOCKER_IMAGE_TAG}"
                    echo "Will tag image as ${DOCKER_IMAGE_FULL_NAME}"

                    sh "docker build -t ${DOCKER_IMAGE_FULL_NAME} ."
                }
            }
        }

        stage('Start instance') {
            steps {
                dir("dhis-2/dhis-e2e-test") {
                    sh "IMAGE_NAME=${DOCKER_IMAGE_FULL_NAME} docker-compose -p ${COMPOSE_PARAMETER} up -d"
                }
            }
        }


        stage('Publish image') {
            steps {
                withDockerRegistry([credentialsId: "docker-hub-credentials", url: ""]) {
                    sh "docker push ${DOCKER_IMAGE_FULL_NAME}"
                }
            }
        }
    }

    post {
        always {
            dir("dhis-2/dhis-e2e-test") {
                sh "IMAGE_NAME=${DOCKER_IMAGE_FULL_NAME} docker-compose -p ${COMPOSE_PARAMETER} down"
                sh "IMAGE_NAME=${TESTS_IMAGE_FULL_NAME} docker-compose -f docker-compose.e2e.yml -p ${COMPOSE_PARAMETER} down"
                sh "docker image prune --force --filter 'until=2h' --filter label=stage=intermediate"
            }
        }
    }
}
