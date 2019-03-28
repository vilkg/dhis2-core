pipeline {
  agent any
  stages {
    stage('Integration tests ') {
      parallel {
        stage('Integration tests ') {
          steps {
            sh 'mvn -f dhis-2/pom.xml install -Pintegration'
          }
        }
        stage('Api tests') {
          steps {
            dir(path: 'dhis-2/dhis-e2e-test') {
              sh 'docker-compose up --build -d'
              sh 'mvn test -DbaseUrl=http://localhost:8070/api'
              sh 'docker-compose down -v --rmi all --remove-orphans'
            }

          }
        }
      }
    }
  }
}
