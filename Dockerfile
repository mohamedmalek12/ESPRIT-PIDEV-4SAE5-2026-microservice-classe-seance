# ---- Stage de Build ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilation
COPY src ./src
RUN mvn clean package -B -DskipTests

# ---- Stage d'Exécution ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# On récupère le JAR du stage précédent
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]