pipeline {
    agent any

    tools {
        jdk 'JDK-17'
        maven 'maven3'
    }

    parameters {
        string(name: 'REPO_URL', defaultValue: 'https://github.com/PWN3D777/DevSecOps_SmartCampusUIS.git', description: 'Repositorio Git')
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: 'Rama')
        string(name: 'GIT_COMMIT', defaultValue: '', description: 'Commit exacto a construir')
    }

    stages {
        stage('Git checkout') {
            steps {
                git branch: params.BRANCH_NAME, url: params.REPO_URL
                script {
                    if (params.GIT_COMMIT?.trim()) {
                        sh "git checkout ${params.GIT_COMMIT}"
                    }
                }
                sh 'git rev-parse HEAD > .git_commit'
            }
        }

        stage('Code compile') {
            steps {
                dir('admin_microservice') {
                    sh 'mvn -B clean compile'
                }
                dir('data_microservice') {
                    sh 'mvn -B clean compile'
                }
            }
        }

        stage('Unit Test') {
            steps {
                dir('admin_microservice') {
                    sh 'mvn -B test'
                }
                dir('data_microservice') {
                    sh 'mvn -B test'
                }
            }
        }

        stage('Build artifact') {
            steps {
                dir('admin_microservice') {
                    sh 'mvn -B -DskipTests package'
                }
                dir('data_microservice') {
                    sh 'mvn -B -DskipTests package'
                }
            }
        }
    }

    post {
        always {
            junit '**/target/surefire-reports/*.xml'
            archiveArtifacts artifacts: 'admin_microservice/target/*.jar, data_microservice/target/*.jar, .git_commit, **/Dockerfile', fingerprint: true
        }
    }
}