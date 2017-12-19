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
      java -Xmx8G -Xms80m -jar ./arff2libsvm/arff2libsvm.jar prepare $BASE_FOLDER/data.arff $EMPTY_HAM_COUNT $EMPTY_SPAM_COUNT $SEED
      # ./svm-scale -l 0 $BASE_FOLDER/data.unscaled > $BASE_FOLDER/data.scaled
      ./svm-scale -l 0 $BASE_FOLDER/data.train.unscaled > $BASE_FOLDER/data.train.scaled
      ./svm-scale -l 0 $BASE_FOLDER/data.test.unscaled > $BASE_FOLDER/data.test.scaled

      # grid search
      python ./tools/grid.py -log2c -5,18,2 -log2g "null" -gnuplot null -out $BASE_FOLDER/grid.log -v 2 $BASE_FOLDER/data.train.scaled
      BEST_C=$(tail -1 $BASE_FOLDER/grid.log | cut -d' ' -f1 | cut -d'=' -f2)
      BEST_G=$(tail -1 $BASE_FOLDER/grid.log | cut -d' ' -f2 | cut -d'=' -f2)

      # train
      ./svm-train -c $BEST_C -g $BEST_G -m $CACHE -q $BASE_FOLDER/data.train.scaled $BASE_FOLDER/data.model

      # test
      ./svm-predict $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.model $BASE_FOLDER/data.prediction > /dev/null
      echo "$(date +"%d-%m-%Y %H:%M:%S") $(java -Xmx8G -Xms80m -jar ./arff2libsvm/arff2libsvm.jar evaluate $BASE_FOLDER/data.test.scaled $BASE_FOLDER/data.prediction) (seed = $SEED, c = $BEST_C, g = $BEST_G)"

      # tear down
      cd $BASE_FOLDER && ls $BASE_FOLDER | grep -v arff | grep -v log | xargs rm && cd - > /dev/null
  done
done <$METADATA
