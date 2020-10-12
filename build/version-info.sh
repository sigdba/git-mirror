#!/bin/bash

GIT_REV=$(git rev-parse HEAD)

cat <<EOF
(ns git-mirror.revision)

(def REVISION-INFO {:build-num "${CODEBUILD_BUILD_NUMBER}"
                    :git-ref   "${GIT_REV}"})
EOF
