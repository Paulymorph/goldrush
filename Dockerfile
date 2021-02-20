FROM hseeberger/scala-sbt:15.0.2_1.4.7_2.13.4

COPY . /goldrush/

WORKDIR /goldrush

RUN sbt '; set assemblyJarName in assembly := "app.jar"\
    ; set assemblyOutputPath in assembly := new File("/app/app.jar")\
    ;  assembly'

RUN rm -rf /goldrush

WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
