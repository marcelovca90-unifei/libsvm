#!/bin/bash

CACHE=2048
METADATA="/Users/marcelocysneiros/git/anti-spam-weka-data/2017_BASE2_ARFF/metadataUpTo1024.txt"
PRIMES=(2)
export OMP_NUM_THREADS=2

while read p; do
  BASE_FOLDER=$(echo $p | cut -d',' -f1 | sed -e "s/~/\/Users\/marcelocysneiros/g")
  EMPTY_HAM_COUNT=$(echo $p | cut -d',' -f2)
  EMPTY_SPAM_COUNT=$(echo $p | cut -d',' -f3)
  echo $(date +"%d-%m-%Y %H:%M:%S") $BASE_FOLDER

  for SEED in "${PRIMES[@]}"
  do
      # prepare
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar prepare $BASE_FOLDER/data.arff $EMPTY_HAM_COUNT $EMPTY_SPAM_COUNT $SEED

      #scale
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar scale $BASE_FOLDER/data.train.unscaled
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar scale $BASE_FOLDER/data.test.unscaled

      # train
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar train $BASE_FOLDER/data.train.scaled

      # test
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar test $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.train.model

      # evaluate
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar evaluate $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.test.prediction

      # tear down
      # cd $BASE_FOLDER && ls $BASE_FOLDER | grep -v arff | grep -v log | xargs rm && cd - > /dev/null
  done
done <$METADATA
