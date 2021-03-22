#!/bin/bash

INPUT_PARAM=""

if [ $# -eq 0 ]; then
  echo "No arguments supplied. Default selected" >&2
  INPUT_PARAM="DEFAULT"
else
  INPUT_PARAM=$1
fi

DOCKER_TAG="$(date +%F_%H-%M-%S)__${USER}__${INPUT_PARAM}"
DOCKER_TAG=`echo "$DOCKER_TAG" | tr '[:upper:]' '[:lower:]'`
echo "DOCKER_TAG=$DOCKER_TAG"

echo "package goldrush\nobject DockerTag { val dockerTag: String = \"$DOCKER_TAG\" }" >./src/main/scala/goldrush/DockerTag.scala

cp -r ./src ./src-backups/$DOCKER_TAG

git add .
git commit -m "$DOCKER_TAG"
git push origin HEAD:$DOCKER_TAG
git reset HEAD~1 --soft

docker build -t "$DOCKER_TAG" ./
docker tag "$DOCKER_TAG" stor.highloadcup.ru/rally/utopian_falcon
docker push stor.highloadcup.ru/rally/utopian_falcon
