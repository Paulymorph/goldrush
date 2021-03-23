#!/bin/bash

if [ $# -eq 0 ]
  then
    echo "No arguments supplied"
    exit 1;
fi

DOCKER_TAG="$1__$(date +%F_%H-%M-%S)";

echo "package goldrush\nobject DockerTag { val dockerTag: String = \"$DOCKER_TAG\" }" > ./src/main/scala/goldrush/DockerTag.scala

docker build -t $1 ./;
docker tag $1 stor.highloadcup.ru/rally/utopian_falcon;
docker push stor.highloadcup.ru/rally/utopian_falcon;

