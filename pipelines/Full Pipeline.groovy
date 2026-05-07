pipeline {
    agent any

    stages {
        stage('CI-01') {
            steps {
                build job: 'CI-01 Source Compile - Unit Validation'
            }
        }

        stage('CI-02') {
            steps {
                build job: 'CI-02 Security - Quality Gate'
            }
        }
        stage('CD-01') {
            steps {
                build job: 'CD-01 Containerization - Deployment'
            }
        }
    }
}