FROM sbtscala/scala-sbt:eclipse-temurin-17.0.4_1.7.1_3.2.0 AS builder
COPY Harness.java /usr/src
COPY build.gradle /usr/src

RUN apt-get update -y && \
    apt-get install -y software-properties-common && \
    add-apt-repository ppa:cwchien/gradle && \
    apt-get update -y && \
    apt-get install -y gradle-8.3 && \
    apt-get clean 


COPY Harness.java /usr/src
COPY build.gradle /usr/src
COPY mjs.jar /usr/src/mjs.jar
WORKDIR /usr/src/
RUN gradle jar --no-daemon

FROM bellsoft/liberica-openjdk-alpine:21
COPY --from=builder /usr/src/build/libs /usr/src
CMD ["java", "-jar", "/usr/src/validator.jar"]