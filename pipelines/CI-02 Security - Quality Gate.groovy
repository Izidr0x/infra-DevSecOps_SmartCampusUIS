pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'maven3'
    }

    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        NVD_API_KEY = credentials('nvd-api-key')
    }

    stages {
        stage('Get Artifacts from Pipeline 1') {
            steps {
                copyArtifacts(
                    projectName: 'CI-01 Source Compile - Unit Validation',
                    selector: [$class: 'StatusBuildSelector', stable: false],
                    filter: '.git_commit, admin_microservice/target/*.jar, data_microservice/application/target/*.jar'
                )
            }
        }

        stage('Git checkout') {
            steps {
                script {
                    env.SOURCE_COMMIT = readFile('.git_commit').trim()
                }

                git branch: 'main', url: 'https://github.com/PWN3D777/DevSecOps_SmartCampusUIS.git'

                sh "git checkout ${SOURCE_COMMIT}"
            }
        }

        stage('Prepare binaries for analysis') {
            steps {
                dir('admin_microservice') {
                    sh 'mvn -B -DskipTests compile'
                }

                dir('data_microservice') {
                    sh 'mvn -B -DskipTests compile'
                }
            }
        }

        stage('Sonarqube Analysis') {
            steps {
                withSonarQubeEnv('Sonarqube-server') {
                    dir('admin_microservice') {
                        sh '''
                            $SCANNER_HOME/bin/sonar-scanner \
                            -Dsonar.projectName=prb-admin \
                            -Dsonar.projectKey=prb-admin \
                            -Dsonar.java.binaries=target/classes
                        '''
                    }

                    dir('data_microservice') {
                        sh '''
                            $SCANNER_HOME/bin/sonar-scanner \
                            -Dsonar.projectName=pruebadata \
                            -Dsonar.projectKey=pruebadata \
                            -Dsonar.java.binaries=application/target/classes,domain/target/classes,service/target/classes,persistence/target/classes
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    def qg = waitForQualityGate abortPipeline: false
                    echo "Quality Gate status recibido por Jenkins: ${qg.status}"
        
                    if (qg.status != 'OK') {
                        error "Quality Gate falló con estado: ${qg.status}"
                    }
                }
            }
        }

        stage('OWASP CHECK') {
            steps {
                dependencyCheck additionalArguments: """
                --scan admin_microservice
                --scan data_microservice
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
            archiveArtifacts artifacts: 'admin_microservice/target/*.jar, data_microservice/application/target/*.jar, .git_commit, **/Dockerfile, dependency-check-report.xml', fingerprint: true, allowEmptyArchive: true
        }
    }
}
