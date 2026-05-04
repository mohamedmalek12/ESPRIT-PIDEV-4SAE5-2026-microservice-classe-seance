pipeline {
    agent any

    environment {
        IMAGE_NAME         = 'classe-seance'
        SONAR_TOKEN        = credentials('sonar-token')
        SONAR_HOST_URL     = 'http://sonarqube:9000'
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    stages {

        stage('📥 Checkout') {
            steps {
                checkout scm
                echo "✅ Code récupéré"
            }
        }

        stage('🔨 Build & Unit Tests') {
            steps {
                sh 'mvn clean package -B'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('🔍 SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=${IMAGE_NAME} \
                            -Dsonar.projectName=${IMAGE_NAME} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    """
                }
            }
        }
        stage('🐳 Docker Build Verify') {
                    steps {
                        script {
                            echo "Vérification de la création de l'image Docker pour Classe-Seance..."
                            // On construit l'image avec un tag de test pour valider le Dockerfile
                            sh "docker build -t ${IMAGE_NAME}:test ."

                            echo "Nettoyage de l'image de test..."
                            sh "docker rmi ${IMAGE_NAME}:test"
                        }
                    }
                }

    }

    post {
        success {
            echo '🎉 Pipeline CI terminé avec succès !'
        }
        failure {
            echo '❌ Pipeline échoué. Vérifiez les logs.'
        }
    }
}