FROM maven:3.6.3-jdk-11-slim@sha256:68ce1cd457891f48d1e137c7d6a4493f60843e84c9e2634e3df1d3d5b381d36c
RUN mkdir /app
WORKDIR /app
COPY . /app
RUN mvn clean package -DskipTests
EXPOSE 8080
CMD "java" "-jar" "target/skipper_demo-1.0-SNAPSHOT.jar" "server"