
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

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
                throw new Exception("usage: java -jar arff2libsvm.jar prepare|evaluate");
        }
    }

    private static void prepare(String[] args) throws Exception, IOException
    {
        System.out.println(LocalTime.now() + " prepare()");

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
        System.out.println(LocalTime.now() + " scale()");

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
        System.out.println(LocalTime.now() + " train()");

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
        System.out.println(LocalTime.now() + " test()");

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
        System.out.println(LocalTime.now() + " evaluate()");

        if (args.length != 3)
            throw new Exception("usage: java -jar arff2libsvm.jar evaluate test_filename prediction_filename");

        String testFilename = args[1];
        String predictionFilename = args[2];

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

        int correct = 0;
        for (int i = 0; i < expected.size(); i++)
            if (expected.get(i) == predicted.get(i))
                correct++;

        double accuracy = 100.0 * (correct) / (expected.size());
        System.out.println(String.format("Accuracy = %.4f%% (%d/%d) (%s)", accuracy, correct, expected.size(), "evaluation-java"));
    }

    private static void run(String[] command, String outputFilename) throws IOException, InterruptedException
    {
        Process p;

        if (outputFilename == null)
            p = new ProcessBuilder(command).start();
        else
            p = new ProcessBuilder(command).redirectOutput(new File(outputFilename)).start();
        p.waitFor();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
        {
            String line = "";
            while ((line = reader.readLine()) != null)
                System.out.println(line);
        }
    }

    private static void balance(List<String> dataset, int seed)
    {
        Random random = new Random(seed);

        int hamCount = (int) dataset.stream().filter(i -> i.startsWith("1 ")).count();
        int spamCount = (int) dataset.stream().filter(i -> i.startsWith("2 ")).count();

        if (hamCount < spamCount)
            for (int i = 0; i < spamCount - hamCount; i++)
                dataset.add(dataset.get(random.nextInt(hamCount)));
        else if (spamCount < hamCount)
            for (int i = 0; i < hamCount - spamCount; i++)
            dataset.add(dataset.get(hamCount + random.nextInt(spamCount)));
    }

    private static void shuffle(List<String> dataset, int seed)
    {
        Random random = new Random(seed);

        int numberOfInstances = dataset.size();
        for (int i = 0; i < numberOfInstances; i++)
        {
            int j = random.nextInt(numberOfInstances);
            String a = dataset.get(i);
            String b = dataset.get(j);
            dataset.set(i, b);
            dataset.set(j, a);
        }
    }

    private static Pair<List<String>, List<String>> split(List<String> dataset, double splitPercent)
    {
        int numberOfInstances = dataset.size();

        List<String> trainSet = new ArrayList<>();
        for (int i = 0; i < (int) (splitPercent * numberOfInstances); i++)
            trainSet.add(dataset.get(i));

        List<String> testSet = new ArrayList<>();
        for (int i = (int) (splitPercent * numberOfInstances); i < numberOfInstances; i++)
            testSet.add(dataset.get(i));

        return Pair.of(trainSet, testSet);
    }

    private static void addEmpty(List<String> testSet, int emptyHamCount, int emptySpamCount)
    {
        for (int i = 0; i < emptyHamCount; i++)
            testSet.add("1");
        for (int i = 0; i < emptySpamCount; i++)
            testSet.add("2");
    }
}
