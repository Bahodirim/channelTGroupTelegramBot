#FROM ubuntu:latest
#LABEL authors="macstore"
#
#ENTRYPOINT ["top", "-b"]


# 1-bosqich: Maven bilan build qilish
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# 2-bosqich: faqat runtime uchun yengil image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]