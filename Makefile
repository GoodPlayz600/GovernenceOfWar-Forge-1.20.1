GRADLEW := ./gradlew
GRADLE_FLAGS := --no-daemon

.PHONY: build test run-client run-server run-gametest clean

build:
	$(GRADLEW) $(GRADLE_FLAGS) build

# Run the reproducible check after the Gradle/Forge dependencies have been
# provisioned by an online bootstrap or the Docker build.
test:
	$(GRADLEW) $(GRADLE_FLAGS) --offline clean build

run-client:
	$(GRADLEW) $(GRADLE_FLAGS) runClient

run-server:
	$(GRADLEW) $(GRADLE_FLAGS) runServer

run-gametest:
	$(GRADLEW) $(GRADLE_FLAGS) runGameTestServer

clean:
	$(GRADLEW) $(GRADLE_FLAGS) clean
