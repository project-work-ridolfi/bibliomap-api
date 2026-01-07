# compilazione con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn package -DskipTests -Dquarkus.package.type=fast-jar

# immagine runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /usr/app

COPY --from=build /usr/src/app/target/quarkus-app/ /usr/app/

EXPOSE 8080

CMD ["java", "-Dquarkus.http.port=${PORT}", "-jar", "quarkus-run.jar"]