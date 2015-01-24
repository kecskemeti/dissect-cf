package test;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

public class TestAll {

	static JUnitCore juc = new JUnitCore();
	public static final int totalRuns = 1000;

	private static long repeatTest(String testName) throws Exception {
		Class testClass = Class.forName(testName);
		long[] results = new long[totalRuns];
		for (int i = 0; i < totalRuns; i++) {
			results[i] = -System.nanoTime();
			juc.run(testClass);
			results[i] += System.nanoTime();
		}
		Arrays.sort(results);
		long sum = 0;
		final int len = (int) (totalRuns * 0.8);
		for (int i = 0; i < len; i++) {
			sum += results[i];
		}
		return sum / len;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Start testing optimized...");

		long totalSum = 0;
		double alpha = 0.6;
		long avg;
		long min;
		long max;
		int numberOfTests = 1;
		String names = "complex.IaaSPerformanceTest "
				+ "complex.ParallelUsageTest "
				+ "simple.DeferredEventTest "
				+ "simple.StorageArtifactsTest "
				+ "simple.TimedTest "
				+ "simple.cloud.AlwaysOnMachinesTest "
				+ "simple.cloud.IaaSServiceTest "
				+ "simple.cloud.NetworkNodeTest "
				+ "simple.cloud.PMTest "
				+ "simple.cloud.PowerMeterTest "
				+ "simple.cloud.RepositoryTest "
				+ "simple.cloud.ResourceConsumptionTest "
				+ "simple.cloud.ResourceSpreadingTest "
				+ "simple.cloud.SchedulerDependentTest "
				+ "simple.cloud.VMTest";
		String[] tests = names.split(" ");

		juc.addListener(new TextListener(new PrintStream(new File("NUL"))));

		System.out.print("Test 1: ");
		for (String clname : tests) {
			System.out.print(".");
			totalSum += repeatTest("at.ac.uibk.dps.cloud.simulator.test." + clname);
		}
		System.out.print(" " + totalSum + "\n");
		avg = totalSum;
		min = totalSum;
		max = totalSum;

		for (int i = 2; i <= numberOfTests; i++) {
			System.out.print("Test " + i + ": ");
			totalSum = 0;
			for (String clname : tests) {
				System.out.print(".");
				totalSum += repeatTest("at.ac.uibk.dps.cloud.simulator.test." + clname);
			}
			System.out.print(" " + totalSum + "\n");
			avg += totalSum;
			if (totalSum > max) {
				max = totalSum;
			}
			if (totalSum < min) {
				min = totalSum;
			}
		}
		avg /= numberOfTests;

		System.out.println("Average: " + avg);
		System.out.println("Minimum: " + min);
		System.out.println("Maximum: " + max);
	}
}
