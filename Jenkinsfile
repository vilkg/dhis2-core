pipeline {
  agent any
  stages {
    stage('IT') {
      steps {
        sh 'mvn -f dhis-2/pom.xml install -Pintegration'
      }
    }
  }
}
