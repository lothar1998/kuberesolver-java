FROM gradle:8.5.0-jdk21 AS build

WORKDIR /kuberesolver/
COPY ../../ ./

WORKDIR /kuberesolver/integration/app
RUN gradle :app:shadowJar

FROM eclipse-temurin:21-jre AS client

WORKDIR /app
COPY --from=build /kuberesolver/integration/app/build/libs/app-all.jar app.jar
ENTRYPOINT ["java", \
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED", \
            "-cp", "app.jar", \
            "io.github.lothar1998.kuberesolver.integration.app.Client", \
            "--target", "kubernetes:///server", \
            "--port", "50052" \
]

FROM eclipse-temurin:21-jre AS server

WORKDIR /app
COPY --from=build /kuberesolver/integration/app/build/libs/app-all.jar app.jar
ENTRYPOINT ["java", \
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED", \
            "-cp", "app.jar", \
            "io.github.lothar1998.kuberesolver.integration.app.Server", \
            "--port", "50051" \
]
