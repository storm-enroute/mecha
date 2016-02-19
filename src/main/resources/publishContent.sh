#!/bin/sh

set -x

PROJECT_NAME="${1}"
VERSION="${2}"
SCALA_VERSION="${3}"
REPO_GIT_URL="${4}"
BRANCH="${5}"
CONTENT_SUBDIR="${6}"
CONTENT_SOURCE_PATH="${7}"
REMOVE_BEFORE_DATE="${8}"
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

# Remove outdated directories.
if [ ! -z "$REMOVE_BEFORE_DATE" ]; then
  cd $CONTENT_SUBDIR
  cd ..
  pwd
  for FILE in *; do
    echo "Checking age of $FILE"
    DAT=`echo $FILE | grep -o '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'`
    if [ ! -z $DAT ]; then
      if [ "`date --date="$DAT" +%s`" -lt "`date --date="$REMOVE_BEFORE_DATE" +%s`" ]
      then
        echo "Removing old entry: $FILE"
        rm -rf $FILE
      fi
    fi
  done
fi
cd $WORKING_DIR

echo "Adding $CONTENT_SUBDIR."
git add $CONTENT_SUBDIR
git add -u
git commit --amend -m "Updating $DIR_NAME." --quiet
echo "Committed $CONTENT_SUBDIR."
git push --force

cd ..
rm -rf $WORKING_DIR
