#!/bin/bash

CACHE=2048
METADATA="/home/marcelovca90/git/anti-spam-weka-data/2017_BASE2_ARFF/metadataUpTo1024.txt"
PRIMES=(2 3 5)
export OMP_NUM_THREADS=2

while read p; do
  FOLDER=$(echo $p | cut -d',' -f1 | sed -e "s/~/\/home\/marcelovca90/g")
  EMPTY_HAM_COUNT=$(echo $p | cut -d',' -f2)
  EMPTY_SPAM_COUNT=$(echo $p | cut -d',' -f3)

  for SEED in "${PRIMES[@]}"
  do
      # prepare
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar prepare $FOLDER/data.arff $EMPTY_HAM_COUNT $EMPTY_SPAM_COUNT $SEED

      #scale
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar scale $FOLDER/data.train.unscaled
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar scale $FOLDER/data.test.unscaled

      # train
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar train $FOLDER/data.train.scaled

      # test
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar test $FOLDER/data.test.scaled $FOLDER/data.model > /dev/null

      # evaluate
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar evaluate $FOLDER/data.test.scaled $FOLDER/data.prediction
  done
      # aggregate
      java -Xmx8G -Xms80m -jar ./arff2libsvm.jar aggregate $FOLDER/data.partial_results $FOLDER/data.train_times $FOLDER/data.test_times

      # tear down
      cd $FOLDER && ls $FOLDER | grep -v arff | xargs rm && cd - > /dev/null
done <$METADATA
