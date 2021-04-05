FROM hseeberger/scala-sbt:15.0.2_1.4.7_2.13.4 AS build
COPY . /goldrush/
WORKDIR /goldrush
RUN sbt '; set assemblyJarName in assembly := "app.jar"\
    ; set assemblyOutputPath in assembly := new File("/app/app.jar")\
    ;  assembly'

FROM openjdk:15.0.2 AS run
WORKDIR /goldrush
COPY --from=build /app/app.jar app.jar
ENV JAVA_OPTS="-Xms1536m -Xmx2g"
ENV _JAVA_OPTIONS="-Xms1536m -Xmx2g"
ENTRYPOINT ["java", "-jar", "app.jar"]
