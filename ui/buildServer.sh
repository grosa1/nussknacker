#!/usr/bin/env bash

set -e

cd ..
export addDevModel=true
export addDevManager=true
./sbtwrapper "set ThisBuild / packageDoc / publishArtifact := false; set Compile / doc / sources := Seq.empty; dist/Universal/stage"
cd -
