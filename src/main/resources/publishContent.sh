#!/bin/sh

set -x

PROJECT_NAME="${1}"
VERSION="${2}"
SCALA_VERSION="${3}"
REPO_GIT_URL="${4}"
BRANCH="${5}"
CONTENT_SUBDIR="${6}"
CONTENT_SOURCE_PATH="${7}"
TMP_CONTENT_DIR=`mktemp -d`
WORKING_DIR=`mktemp -d`

cp -R $CONTENT_SOURCE_PATH $TMP_CONTENT_DIR

cd $WORKING_DIR
pwd

if [ -d .git ] || git rev-parse --git-dir > /dev/null 2>&1; then
  echo "Already git repo."
else
  git clone $REPO_GIT_URL .
fi

git fetch --all
git checkout $BRANCH

rm -rf $CONTENT_SUBDIR
mkdir --parents $CONTENT_SUBDIR
mv $TMP_CONTENT_DIR/* $CONTENT_SUBDIR
rm -rf $TMP_CONTENT_DIR
echo "Moved content dir."
ls -a
git status

echo "Adding $CONTENT_SUBDIR."
git add $CONTENT_SUBDIR
git commit --amend -m "Updating $DIR_NAME."
echo "Committed $CONTENT_SUBDIR."
ls -a
git push --force

cd ..
rm -rf $WORKING_DIR
