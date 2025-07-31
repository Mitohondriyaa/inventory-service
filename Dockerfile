FROM openjdk:21
WORKDIR /app
COPY target/inventory-service-0.0.1-SNAPSHOT.jar inventory-service.jar
ENTRYPOINT ["java", "-jar", "inventory-service.jar"]