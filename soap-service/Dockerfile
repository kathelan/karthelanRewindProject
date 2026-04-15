FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copy pom files first for better layer caching
COPY pom.xml .
COPY core-common/pom.xml core-common/pom.xml
COPY rest-service-parent/pom.xml rest-service-parent/pom.xml
COPY soap-service/pom.xml soap-service/pom.xml
COPY soap-service/soap-service-api/pom.xml soap-service/soap-service-api/pom.xml
COPY soap-service/soap-service-rest/pom.xml soap-service/soap-service-rest/pom.xml
COPY soap-service/soap-service-client/pom.xml soap-service/soap-service-client/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY user-service/user-service-api/pom.xml user-service/user-service-api/pom.xml
COPY user-service/user-service-rest/pom.xml user-service/user-service-rest/pom.xml
COPY user-service/user-service-client/pom.xml user-service/user-service-client/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/auth-service-api/pom.xml auth-service/auth-service-api/pom.xml
COPY auth-service/auth-service-rest/pom.xml auth-service/auth-service-rest/pom.xml
COPY db-performance/pom.xml db-performance/pom.xml
COPY mapstruct-service/pom.xml mapstruct-service/pom.xml

RUN mvn dependency:go-offline -pl soap-service/soap-service-rest -am -q

COPY . .
RUN mvn clean package -pl soap-service/soap-service-rest -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/soap-service/soap-service-rest/target/*-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
