node {
    // System Dependent Locations
    def mvnTool = tool name: 'maven3', type: 'hudson.tasks.Maven$MavenInstallation'
    def jdk7 = tool name: 'jdk7', type: 'hudson.model.JDK'
    def jdk8 = tool name: 'jdk8', type: 'hudson.model.JDK'
    def jdk9 = tool name: 'jdk9', type: 'hudson.model.JDK'

    // Environment
    List mvnEnv7 = ["PATH+MVN=${mvnTool}/bin", "PATH+JDK=${jdk7}/bin", "JAVA_HOME=${jdk7}/", "MAVEN_HOME=${mvnTool}"]
    mvnEnv7.add("MAVEN_OPTS=-Xms256m -Xmx1024m -Djava.awt.headless=true")
    List mvnEnv8 = ["PATH+MVN=${mvnTool}/bin", "PATH+JDK=${jdk8}/bin", "JAVA_HOME=${jdk8}/", "MAVEN_HOME=${mvnTool}"]
    mvnEnv8.add("MAVEN_OPTS=-Xms256m -Xmx1024m -Djava.awt.headless=true")
    List mvnEnv9 = ["PATH+MVN=${mvnTool}/bin", "PATH+JDK=${jdk9}/bin", "JAVA_HOME=${jdk9}/", "MAVEN_HOME=${mvnTool}"]
    mvnEnv9.add("MAVEN_OPTS=-Xms256m -Xmx1024m -Djava.awt.headless=true")

    stage('Checkout') {
        checkout scm
    }

    stage('Build JDK 7') {
        withEnv(mvnEnv7) {
            timeout(time: 1, unit: 'HOURS') {
                sh "mvn -B clean install -Dmaven.test.failure.ignore=true"
                // Report failures in the jenkins UI
                step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
            }
        }
    }

    stage('Build JDK 8') {
        withEnv(mvnEnv8) {
            timeout(time: 1, unit: 'HOURS') {
                sh "mvn -B clean install -Dmaven.test.failure.ignore=true"
                // Report failures in the jenkins UI.
                step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
                // Collect the JaCoCo execution results.
                step([$class: 'JacocoPublisher',
                      exclusionPattern: '**/org/webtide/**,**/org/cometd/benchmark/**,**/org/cometd/examples/**',
                      execPattern: '**/target/jacoco.exec',
                      classPattern: '**/target/classes',
                      sourcePattern: '**/src/main/java'])
            }
        }
    }

    stage('Build JDK 8 - Jetty 9.3.x') {
        withEnv(mvnEnv8) {
            timeout(time: 1, unit: 'HOURS') {
                sh "mvn -B clean install -Dmaven.test.failure.ignore=true -Djetty-version=9.3.21.v20170918"
                // Report failures in the jenkins UI
                step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
            }
        }
    }

    stage('Build JDK 8 - Jetty 9.4.x') {
        withEnv(mvnEnv8) {
            timeout(time: 1, unit: 'HOURS') {
                sh "mvn -B clean install -Dmaven.test.failure.ignore=true -Djetty-version=9.4.7.v20170914"
                // Report failures in the jenkins UI
                step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
            }
        }
    }

    stage('Build JDK 9 - Jetty 9.4.x') {
        withEnv(mvnEnv9) {
            timeout(time: 1, unit: 'HOURS') {
                sh "mvn -B clean install -Dmaven.test.failure.ignore=true -Djetty-version=9.4.7.v20170914"
                // Report failures in the jenkins UI
                step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
            }
        }
    }

    stage('Javadoc') {
        withEnv(mvnEnv8) {
            timeout(time: 5, unit: 'MINUTES') {
                sh "mvn -B javadoc:javadoc"
            }
        }
    }
}
