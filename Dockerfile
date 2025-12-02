# Stage 1: Build the provider
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Create the custom Keycloak image
FROM quay.io/keycloak/keycloak:26.0.6
COPY --from=builder /app/build/libs/keycloak-kakao-otp-provider-1.0.0.jar /opt/keycloak/providers/

# Build to register the provider
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
