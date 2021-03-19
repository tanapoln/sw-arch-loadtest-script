#!/bin/bash

for i in $(seq 1 10000); do
  nc 13.67.38.126 9000 &
  echo open conn $i
done
