pipeline {
  agent any
  stages {
    stage('Test') {
      parallel {
        stage('Integration tests ') {
          steps {
            sh 'mvn -f dhis-2/pom.xml install -Pintegration'
          }
        }
        stage('Api tests') {
          steps {
            dir(path: 'dhis-2/dhis-e2e-test') {
              sh 'docker-compose up -d'
              sh 'mvn test -DbaseUrl=http://localhost:8070/api'
              sh 'docker-compose down'
            }

          }
        }
      }
    }
  }
}