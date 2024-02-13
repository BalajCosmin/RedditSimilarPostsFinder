FROM ubuntu:latest
LABEL authors="Balaj Cosmin"

# Java Runtime Image
FROM openjdk:17-jdk-alpine

# Make port 8080 available
EXPOSE 8080

# Run the application's .jar file
ARG JAR_FILE=target/RedditSimilarPosts-1.0-SNAPSHOT.jar

ADD ${JAR_FILE} RedditSimilarPosts.jar

ENTRYPOINT ["java","-jar","/RedditSimilarPosts.jar"]
