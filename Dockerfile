# syntax=docker/dockerfile:1.7
# One build stage compiles the whole multi-module project once; each service is a small
# runtime target:  docker build --target ledger-service -t gigpay/ledger-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY contracts/pom.xml contracts/
COPY services/gateway/pom.xml services/gateway/
COPY services/earnings-service/pom.xml services/earnings-service/
COPY services/ledger-service/pom.xml services/ledger-service/
COPY services/coach-service/pom.xml services/coach-service/
COPY contracts/src contracts/src
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN useradd --system --uid 10001 app
WORKDIR /app
USER 10001
# Size the heap from the container's memory limit, and fail fast instead of limping on after OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS gateway
COPY --from=build /src/services/gateway/target/gateway-1.0.0.jar /app/app.jar
EXPOSE 8080

FROM runtime AS earnings-service
COPY --from=build /src/services/earnings-service/target/earnings-service-1.0.0.jar /app/app.jar
EXPOSE 8101

FROM runtime AS ledger-service
COPY --from=build /src/services/ledger-service/target/ledger-service-1.0.0.jar /app/app.jar
EXPOSE 8102

FROM runtime AS coach-service
COPY --from=build /src/services/coach-service/target/coach-service-1.0.0.jar /app/app.jar
EXPOSE 8103
