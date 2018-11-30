#!groovy

pipeline {
    agent any
    tools {
        maven 'Maven'
        jdk 'JDK1.8'
    }
    triggers {
        pollSCM('H H 1 1 *')
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
        stage('Release') {
            environment {
                GITHUB_CREDS = credentials('GitHub')
                GITHUB_CONFIG = /git config user.email knazarova9@gmail.com && git config user.name Jenkins/
                MVN_RELEASE_FORMAT = /mvn release:prepare release:perform -DreleaseVersion=%s -Dusername=%s -Dpassword=%s -Darguments="-Dmaven.javadoc.skip=true"/
            }
            steps {
                junit 'target/surefire-reports/TEST-*.xml'
                withCredentials([usernameColonPassword(credentialsId: 'GitHub', variable: 'GITHUB_CREDS')]) {
                    script {
                        if (isUnix()) {
                            sh(GITHUB_CONFIG)
                            sh(String.format(MVN_RELEASE_FORMAT, currentBuild.displayName, GITHUB_CREDS_USR, GITHUB_CREDS_PSW))
                        } else {
                            bat(GITHUB_CONFIG)
                            bat(String.format(MVN_RELEASE_FORMAT, currentBuild.displayName, GITHUB_CREDS_USR, GITHUB_CREDS_PSW))
                        }
                    }
                }
            }
        }
        /*stage('Results') {
            steps {
                junit 'target/surefire-reports/TEST-*.xml'
                archiveArtifacts 'target/*.jar'
            }
        }*/
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
