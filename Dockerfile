FROM openjdk:11
COPY target/scala-2.12/consumer-raw-text.jar /
ENTRYPOINT ["java","-jar","consumer-raw-text.jar"]