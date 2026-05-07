
pipeline {
    agent any
    environment {
    SCANNER_HOME = tool 'sonar-scanner'
    NVD_API_KEY = credentials('nvd-api-key')
    }
    stages {
        stage('Get Artifacts from Pipeline 1') {
            steps {
                copyArtifacts(
                    projectName: 'CI-01 Source Compile - Unit Validation',
                    selector: [$class: 'StatusBuildSelector', stable: false]
                )
            }
        }
        stage('Sonarqube Analysis') {
            steps {
                withSonarQubeEnv('Sonarqube-server') {
                     dir('admin_microservice') {
                        sh ''' $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=prb-admin \
                        -Dsonar.java.binaries=. \
                        -Dsonar.projectKey=prb-admin '''
                    }

                    dir('data_microservice') {
                        sh ''' $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=pruebadata \
                        -Dsonar.java.binaries=. \
                        -Dsonar.projectKey=pruebadata '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                waitForQualityGate abortPipeline: true
            }
        }   
        stage('OWASP CHECK'){
            steps{
                
                dependencyCheck additionalArguments: """
                --scan ./
                --format XML
                --nvdApiKey ${NVD_API_KEY}
                --nvdApiDelay 6000
                --out .
                """, odcInstallation: 'Check-DP'

                dependencyCheckPublisher pattern: 'dependency-check-report.xml', stopBuild: false
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'admin_microservice/target/*.jar, data_microservice/target/*.jar, .git_commit, **/Dockerfile', fingerprint: true
        }
    }
}
