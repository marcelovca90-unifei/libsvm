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

package io.github.marcelovca90.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class Utils
{
    public static void run(String[] command, String outputFilename) throws IOException, InterruptedException
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

    public static void balance(List<String> dataset, int seed)
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

    public static void shuffle(List<String> dataset, int seed)
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

    public static Pair<List<String>, List<String>> split(List<String> dataset, double splitPercent)
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

    public static void addEmpty(List<String> testSet, int emptyHamCount, int emptySpamCount)
    {
        for (int i = 0; i < emptyHamCount; i++)
            testSet.add("1");
        for (int i = 0; i < emptySpamCount; i++)
            testSet.add("2");
    }

    public static String format(double v)
    {
        return StringUtils.rightPad(String.format("%.2f", v), 10);
    }
}
