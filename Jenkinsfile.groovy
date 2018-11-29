#!groovy

pipeline {
    agent any
    tools {
        maven 'Maven'
        jdk 'JDK1.8'
    }
    triggers {
        pollSCM('H/5 * * * *')
    }
    stages {
        stage('SCM') {
            steps {
                git credentialsId: 'GitHub', url: 'https://github.com/KseniiaNazarova/spring-petclinic'
            }
        }
        stage('Build') {
            environment {
                BUILD_NAME = VersionNumber(projectStartDate: '2018-11-28',
                        versionNumberString: '${BUILD_DAY, XX}.${BUILD_MONTH, XX}.${BUILDS_THIS_YEAR, XXX}',
                        versionPrefix: 'v',
                        worstResultForIncrement: 'SUCCESS',
                )
            }
            steps {

                script {
                    currentBuild.displayName = "$BUILD_NAME"
                }

                bat(/mvn -Dmaven.test.failure.ignore=false clean package/)
            }
        }
        stage("SonarQube analysis") {
            environment {
                scannerHome = tool 'sonar-scanner-3.2.0.1227'
            }
            steps {
                withSonarQubeEnv('Sonar') {
                    bat("""${scannerHome}/bin/sonar-scanner -Dsonar.java.source-1.8 -Dsonar.java.binaries=target/classes/""")
                }
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        stage('Results') {
            steps {
                junit '**/target/surefire-reports/TEST-*.xml'
                archive 'target/*.jar'
            }
        }
    }
    post {
        failure {
            echo "Try email notification"
            emailext attachLog: true,
                    body: '$DEFAULT_CONTENT',
                    compressLog: true,
                    to: 'knazarova9@gmail.com',
                    recipientProviders: [culprits()],
                    subject: '$DEFAULT_SUBJECT'
        }
    }
}
