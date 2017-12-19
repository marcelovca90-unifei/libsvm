/*******************************************************************************
 * Copyright (C) 2017 Marcelo Vinícius Cysneiros Aragão
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/

package io.github.marcelovca90.main;

import static io.github.marcelovca90.util.Utils.addEmpty;
import static io.github.marcelovca90.util.Utils.balance;
import static io.github.marcelovca90.util.Utils.format;
import static io.github.marcelovca90.util.Utils.run;
import static io.github.marcelovca90.util.Utils.shuffle;
import static io.github.marcelovca90.util.Utils.split;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.github.habernal.confusionmatrix.ConfusionMatrix;

public class Runner
{
    private static final String USER_HOME = System.getProperty("user.home");

    public static void main(String[] args) throws Exception
    {
        if (args == null || args.length == 0)
            args = new String[]
            { "empty" };

        switch (args[0])
        {
            case "prepare":
                prepare(args);
                break;

            case "scale":
                scale(args);
                break;

            case "train":
                train(args);
                break;

            case "test":
                test(args);
                break;

            case "evaluate":
                evaluate(args);
                break;

            default:
                throw new Exception("usage: java -jar arff2libsvm.jar prepare|scale|train|test|evaluate");
        }
    }

    private static void prepare(String[] args) throws Exception, IOException
    {
        if (args.length != 5)
            throw new Exception("usage: java -jar arff2libsvm.jar prepare arff_filename empty_ham_count empty_spam_count seed");

        String arffFilename = args[1];
        int emptyHamCount = Integer.parseInt(args[2]);
        int emptySpamCount = Integer.parseInt(args[3]);
        int seed = Integer.parseInt(args[4]);

        String output = arffFilename.replace("data.arff", "data.unscaled");

        StringBuilder sb = new StringBuilder();

        List<String> dataset = new ArrayList<>();

        Files.readAllLines(Paths.get(arffFilename)).stream().forEach(line -> {
            int y = line.endsWith("HAM") ? 1 : line.endsWith("SPAM") ? 2 : Integer.MIN_VALUE;

            if (y != Integer.MIN_VALUE)
            {
                String[] parts = line.split(",");

                sb.append(y);
                for (int i = 0; i < parts.length - 1; i++)
                    sb.append(String.format(" %d:%s", i + 1, parts[i]));

                dataset.add(sb.toString());
                sb.setLength(0);
            }
        });

        balance(dataset, seed);
        shuffle(dataset, seed);
        FileUtils.writeLines(new File(output), dataset);

        Pair<List<String>, List<String>> datasets = split(dataset, 0.5);

        List<String> trainSet = datasets.getLeft();
        FileUtils.writeLines(new File(output.replace("data.unscaled", "data.train.unscaled")), trainSet);

        List<String> testSet = datasets.getRight();
        addEmpty(testSet, emptyHamCount, emptySpamCount);
        FileUtils.writeLines(new File(output.replace("data.unscaled", "data.test.unscaled")), testSet);
    }

    private static void scale(String[] args) throws Exception
    {
        if (args.length != 2)
            throw new Exception("usage: java -jar arff2libsvm.jar scale data_filename");

        String data_filename = args[1];
        String output_filename = data_filename.replaceAll(".unscaled", ".scaled");

        String[] cmd = new String[]
        {
                USER_HOME + "/git/libsvm-marcelovca90/svm-scale",
                "-l",
                "0",
                data_filename
        };

        run(cmd, output_filename);
    }

    private static void train(String[] args) throws Exception
    {
        if (args.length != 2)
            throw new Exception("usage: java -jar arff2libsvm.jar train training_set_file");

        String training_set_file = args[1];
        String model_file = training_set_file.replaceAll(".scaled", ".model");

        String[] cmd = new String[]
        {
                USER_HOME + "/git/libsvm-marcelovca90/svm-train",
                "-m",
                "2048.0",
                "-q",
                training_set_file,
                model_file
        };

        run(cmd, null);
    }

    private static void test(String[] args) throws Exception
    {
        if (args.length != 3)
            throw new Exception("usage: java -jar arff2libsvm.jar test test_file model_file");

        String test_file = args[1];
        String model_file = args[2];
        String output_file = test_file.replaceAll(".scaled", ".prediction");

        String[] cmd = new String[]
        {
                USER_HOME + "/git/libsvm-marcelovca90/svm-predict",
                test_file,
                model_file,
                output_file
        };

        run(cmd, null);
    }

    private static void evaluate(String[] args) throws Exception
    {
        if (args.length != 3)
            throw new Exception("usage: java -jar arff2libsvm.jar evaluate test_filename prediction_filename");

        String testFilename = args[1];
        String predictionFilename = args[2];
        String shortFilename = testFilename.substring(testFilename.lastIndexOf("2017"));
        shortFilename = shortFilename.substring(0, shortFilename.lastIndexOf(File.separator));

        List<Integer> expected = Files
                .readAllLines(Paths.get(testFilename))
                .stream()
                .map(line -> Character.getNumericValue(line.charAt(0)))
                .collect(Collectors.toList());

        List<Integer> predicted = Files
                .readAllLines(Paths.get(predictionFilename))
                .stream()
                .map(line -> Character.getNumericValue(line.charAt(0)))
                .collect(Collectors.toList());

        if (expected.size() != predicted.size())
            throw new Exception("expected.size() != predicted.size()");

        int ham_spam = 0, ham_ham = 0, spam_ham = 0, spam_spam = 0;

        for (int i = 0; i < expected.size(); i++)
        {
            if (expected.get(i) == 1 && predicted.get(i) == 1)
                ham_ham++;
            if (expected.get(i) == 1 && predicted.get(i) == 2)
                ham_spam++;
            if (expected.get(i) == 2 && predicted.get(i) == 1)
                spam_ham++;
            if (expected.get(i) == 2 && predicted.get(i) == 2)
                spam_spam++;
        }

        ConfusionMatrix cm = new ConfusionMatrix();

        cm.increaseValue("ham", "ham", ham_ham);
        cm.increaseValue("ham", "spam", ham_spam);
        cm.increaseValue("spam", "ham", spam_ham);
        cm.increaseValue("spam", "spam", spam_spam);

        double fMeasure = 2.0 * (1.0 / ((1.0 / cm.getAvgRecall()) + (1.0 / cm.getAvgPrecision())));

        System.out.println(
                String.format("%s %s hamPrecision=%s spamPrecision=%s hamRecall=%s spamRecall=%s fMeasure=%s",
                        LocalDate.now() + " " + LocalTime.now(),
                        StringUtils.rightPad(shortFilename, 40),
                        format(100.0 * cm.getPrecisionForLabel("ham")),
                        format(100.0 * cm.getPrecisionForLabel("spam")),
                        format(100.0 * cm.getRecallForLabel("ham")),
                        format(100.0 * cm.getRecallForLabel("spam")),
                        format(100.0 * fMeasure)));
    }
}
