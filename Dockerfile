FROM maven:3.8.1-openjdk-17-slim AS maven-build
WORKDIR /tmp/grpc-sensors
COPY pom.xml ./
COPY proto/pom.xml ./proto/pom.xml
COPY grpc-sensors-server/pom.xml ./grpc-sensors-server/pom.xml
RUN mvn verify --fail-never -Dmaven.test.skip dependency:go-offline

COPY grpc-sensors-server/src ./grpc-sensors-server/src
COPY proto/src ./proto/src
RUN mvn install -Dmaven.test.skip

FROM openjdk:17-alpine
WORKDIR /opt/grpc-sensors
COPY --from=maven-build /tmp/grpc-sensors/grpc-sensors-server/target/grpc-sensors-server.jar ./grpc-sensors-server.jar
COPY --from=maven-build /tmp/grpc-sensors/grpc-sensors-server/target/grpc-sensors-server.lib ./grpc-sensors-server.lib

ENTRYPOINT ["java","-jar","/opt/grpc-sensors/grpc-sensors-server.jar"]

