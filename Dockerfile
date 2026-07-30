FROM eclipse-temurin:25-jdk AS build

ENV HOME=/app
RUN mkdir -p $HOME
WORKDIR $HOME
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY . $HOME
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:25-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
