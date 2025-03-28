FROM ubuntu:latest

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update -y && apt-get install -y ffmpeg

RUN mkdir /opt/app
ARG JAR_FILE=build/libs/demo-*-SNAPSHOT.jar
COPY ${JAR_FILE} /opt/app/app.jar

RUN addgroup spring && adduser --ingroup spring spring
USER spring:spring

ENTRYPOINT ["java","-jar","/opt/app/app.jar"]