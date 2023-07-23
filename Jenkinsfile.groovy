import java.text.SimpleDateFormat

def TODAY = (new SimpleDateFormat("yyyyMMddHHmmss")).format(new Date())

pipeline {
    agent any
    environment {
        strDockerTag = "${TODAY}_${BUILD_ID}"
        strDockerImage ="seonm096/cicd_guestbook:${strDockerTag}"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'master', url:'http://github.com/seon9604/guestbook.git'
            }
        }
        stage('Build') {
            steps {
                sh './mvnw clean package'
            }
        }
        stage('Unit Test') {
            steps {
                sh './mvnw test'
            }
            
            post {
                always {
                    junit '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Docker Image Build') {
            steps {
                script {
                    //oDockImage = docker.build(strDockerImage)
                    oDockImage = docker.build(strDockerImage, "--build-arg VERSION=${strDockerTag} -f Dockerfile .")
                }
            }
        }
        stage('Docker Image Push') {
            steps {
                script {
                    docker.withRegistry('', 'DockerHub_Credential') {
                        oDockImage.push()
                    }
                }
            }
        }

        stage('Staging Deploy') {
            steps {
                sshagent(credentials: ['Staging-PrivateKey']) {
                    sh "ssh -o StrictHostKeyChecking=no ec2-user@43.201.99.129 docker container rm -f guestbookapp"
                    sh "ssh -o StrictHostKeyChecking=no ec2-user@43.201.99.129 docker container run \
                                        -d \ 
                                        --name=guestbookapp \
                                        --network=host \
                                        -e CONTEXT_PATH=/ \
                                        -e MYSQL_IP=172.31.43.139 \
                                        -e MYSQL_PORT=3306 \
                                        -e MYSQL_DATABASE=guestbook \
                                        -e MYSQL_USER=root \
                                        -e MYSQL_PASSWORD=education \
                                        ${strDockerImage} "
                }
            }
        }
    }
}