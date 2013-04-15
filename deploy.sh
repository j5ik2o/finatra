#!/bin/sh

sbt clean +publish
mv repos /tmp
git checkout gh-pages
git clean -fdx
cp -rp /tmp/repos .
git add repos
git commit repos -m "deploy"
