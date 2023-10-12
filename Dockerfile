FROM sbtscala/scala-sbt:eclipse-temurin-17.0.4_1.7.2_2.13.10 AS builder

COPY Harness.scala /usr/src
COPY build.sbt /usr/src
COPY project /usr/src/project
COPY lib /usr/src/lib
WORKDIR /usr/src/
RUN sbt assembly

ENTRYPOINT [ "/usr/bin/bash" ]

FROM eclipse-temurin:17-jre
COPY --from=builder /usr/src/target/scala-2.13/validator.jar /usr/src
CMD ["java", "-Xss8m", "-Xmx16g", "-jar", "/usr/src/validator.jar"]

