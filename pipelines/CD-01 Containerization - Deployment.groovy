pipeline {
    agent any

    tools {
        dockerTool 'docker'
    }

    environment {
        DOCKER_NAMESPACE = 'izidr0x'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Git checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/PWN3D777/DevSecOps_SmartCampusUIS.git'
            }
        }

        stage('Get Artifacts from CI-01') {
            steps {
                copyArtifacts(
                    projectName: 'CI-01 Source Compile - Unit Validation',
                    selector: [$class: 'StatusBuildSelector', stable: false],
                    filter: 'admin_microservice/target/*.jar, data_microservice/application/target/*.jar'
                )
            }
        }

        stage('Docker build') {
            steps {
                sh """
                    docker build -t ${DOCKER_NAMESPACE}/adminprb:${IMAGE_TAG} ./admin_microservice
                    docker build -t ${DOCKER_NAMESPACE}/data:${IMAGE_TAG} ./data_microservice

                    docker tag ${DOCKER_NAMESPACE}/adminprb:${IMAGE_TAG} ${DOCKER_NAMESPACE}/adminprb:latest
                    docker tag ${DOCKER_NAMESPACE}/data:${IMAGE_TAG} ${DOCKER_NAMESPACE}/data:latest
                """
            }
        }

        stage('Trivy scan') {
            steps {
                sh '''
                    mkdir -p trivy-reports

                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v "$PWD/trivy-reports:/reports" \
                      aquasec/trivy:latest image \
                      --severity HIGH,CRITICAL \
                      --exit-code 0 \
                      --format table \
                      --no-progress \
                      ${DOCKER_NAMESPACE}/adminprb:${IMAGE_TAG} \
                      > trivy-reports/admin-trivy.txt 2>&1 || true

                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v "$PWD/trivy-reports:/reports" \
                      aquasec/trivy:latest image \
                      --severity HIGH,CRITICAL \
                      --exit-code 0 \
                      --format table \
                      --no-progress \
                      ${DOCKER_NAMESPACE}/data:${IMAGE_TAG} \
                      > trivy-reports/data-trivy.txt 2>&1 || true
                '''
            }

            post {
                always {
                    archiveArtifacts artifacts: 'trivy-reports/*.txt', fingerprint: true, allowEmptyArchive: true
                }
            }
        }

        stage('Docker push') {
            steps {
                withCredentials([usernamePassword(credentialsId: '2', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin

                        docker push ${DOCKER_NAMESPACE}/adminprb:${IMAGE_TAG}
                        docker push ${DOCKER_NAMESPACE}/data:${IMAGE_TAG}
                        docker push ${DOCKER_NAMESPACE}/adminprb:latest
                        docker push ${DOCKER_NAMESPACE}/data:latest
                    """
                }
            }
        }

        stage('Deploy dependencies') {
            steps {
                sh 'docker compose up -d db mongo emqx influxdb rabbitmq minio'
            }
        }

        stage('Wait dependencies') {
            steps {
                sh 'sleep 20'
            }
        }

        stage('Deploy admin and data') {
            steps {
                sh 'docker compose up -d admin data'
            }
        }

        stage('Wait core services') {
            steps {
                sh 'sleep 20'
            }
        }

        stage('Deploy remaining services') {
            steps {
                sh 'docker compose up -d gateway frontend grafana'
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    set -e
        
                    HOST_IP="192.168.1.141"
        
                    echo "=== Estado de servicios ==="
                    docker compose ps
        
                    for svc in db mongo emqx influxdb rabbitmq minio admin data gateway frontend grafana; do
                      docker compose ps --services --filter status=running | grep -x "$svc" >/dev/null || {
                        echo "FAIL: $svc no está corriendo"
                        exit 1
                      }
                    done
        
                    check_http() {
                      NAME="$1"
                      URL="$2"
                      CODE=$(curl -s -o /dev/null -w "%{http_code}" "$URL" || true)
        
                      if [ "$CODE" = "000" ]; then
                        echo "FAIL: $NAME no responde en $URL"
                        exit 1
                      else
                        echo "PASS: $NAME responde con HTTP $CODE en $URL"
                      fi
                    }
        
                    check_http "data" "http://${HOST_IP}:8082/actuator/health"
                    check_http "gateway" "http://${HOST_IP}:8080/"
                    check_http "frontend" "http://${HOST_IP}:4000/"
                '''
            }
        }
    }
}
