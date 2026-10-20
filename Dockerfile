FROM eclipse-temurin:17-jdk-jammy

WORKDIR /workspace

COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon build

# This is a development image. The ForgeGradle server run is useful for
# smoke-testing the mod; production server packaging is documented in README.
CMD ["./gradlew", "--no-daemon", "runServer"]
