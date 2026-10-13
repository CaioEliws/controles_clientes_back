FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM maven:3.9-eclipse-temurin-17 AS audit
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer fonts-crosextra-carlito fonts-liberation2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace

FROM eclipse-temurin:17-jre-noble AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer fonts-crosextra-carlito fonts-liberation2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
