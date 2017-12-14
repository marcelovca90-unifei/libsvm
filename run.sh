#!/bin/bash

CACHE=2048
METADATA="/Users/marcelocysneiros/git/anti-spam-weka-data/2017_BASE2_ARFF/metadataLingSpam.txt"
#PRIMES=(2 3 5 7 11 13 17 19 23 29)
PRIMES=(2)

while read p; do
  BASE_FOLDER=$(echo $p | cut -d',' -f1 | sed -e "s/~/\/Users\/marcelocysneiros/g")
  EMPTY_HAM_COUNT=$(echo $p | cut -d',' -f2)
  EMPTY_SPAM_COUNT=$(echo $p | cut -d',' -f3)
  echo $BASE_FOLDER

  for SEED in "${PRIMES[@]}"
  do
      # prepare
      java -jar ./ArffToLibSVM.jar prepare $BASE_FOLDER/data.arff $EMPTY_HAM_COUNT $EMPTY_SPAM_COUNT $SEED
      ./svm-scale -l 0 $BASE_FOLDER/data.unscaled > $BASE_FOLDER/data.scaled
      ./svm-scale -l 0 $BASE_FOLDER/data.train.unscaled > $BASE_FOLDER/data.train.scaled
      ./svm-scale -l 0 $BASE_FOLDER/data.test.unscaled > $BASE_FOLDER/data.test.scaled

      # grid search
      PARAMS=$(python ./tools/grid.py -log2c -5,5,1 -log2g -4,0,1 -gnuplot null -out null -v 5 $BASE_FOLDER/data.train.scaled)
      BEST_C=$(echo $PARAMS | cut -d' ' -f1)
      BEST_G=$(echo $PARAMS | cut -d' ' -f2)

      # train
      ./svm-train -c $BEST_C -g $BEST_G -m $CACHE -q $BASE_FOLDER/data.train.scaled $BASE_FOLDER/data.model

      # test
      ./svm-predict $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.model $BASE_FOLDER/data.prediction > /dev/null

      echo "    $(java -jar ./ArffToLibSVM.jar evaluate $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.prediction) (seed = $SEED, c = $BEST_C, g = $BEST_G)"
  done
done <$METADATA
