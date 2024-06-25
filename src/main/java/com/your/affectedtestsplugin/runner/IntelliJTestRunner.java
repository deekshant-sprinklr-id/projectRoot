package com.your.affectedtestsplugin.runner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Utility class for running JUnit tests within an IntelliJ project.
 */
public class IntelliJTestRunner {

    /**
     * Runs the specified set of JUnit test methods within the given IntelliJ project.
     *
     * @param project     The IntelliJ project in which to run the tests.
     * @param testMethods The set of test methods to be run.
     * @param latch       The CountDownLatch to synchronize the test run completion.
     */
    public static void runTests(Project project, Set<PsiMethod> testMethods, CountDownLatch latch) {
        RunnerAndConfigurationSettings settings = createTestConfiguration(project, testMethods);
        ExecutionEnvironment environment = buildExecutionEnvironment(settings, latch);
        ApplicationManager.getApplication().invokeLater(() -> startRunProfile(project, environment, latch));
    }

    /**
     * Runs the specified set of JUnit test methods within the given IntelliJ project for the previous state.
     *
     * @param project     The IntelliJ project in which to run the tests.
     * @param testMethods The set of test methods to be run.
     */
    public static void runTestsForPrevious(Project project, Set<PsiMethod> testMethods) {
        RunnerAndConfigurationSettings settings = createTestConfiguration(project, testMethods);
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    /**
     * Creates a JUnit test configuration for the specified test methods.
     *
     * @param project     The IntelliJ project.
     * @param testMethods The set of test methods to be included in the configuration.
     * @return The created RunnerAndConfigurationSettings.
     */
    private static RunnerAndConfigurationSettings createTestConfiguration(Project project, Set<PsiMethod> testMethods) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationType junitConfigType = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory junitConfigFactory = junitConfigType.getConfigurationFactories()[0];

        RunnerAndConfigurationSettings settings = runManager.createConfiguration("RunAffectedTestConfiguration", junitConfigFactory);
        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

        ApplicationManager.getApplication().runReadAction(() -> setupTestConfigurationData(configuration, testMethods));
        configuration.setWorkingDirectory(project.getBasePath());

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);

        return settings;
    }

    /**
     * Sets up the test configuration data with the given test methods.
     *
     * @param configuration The JUnit configuration to set up.
     * @param testMethods   The set of test methods to be run.
     */
    private static void setupTestConfigurationData(JUnitConfiguration configuration, Set<PsiMethod> testMethods) {
        JUnitConfiguration.Data data = configuration.getPersistentData();
        data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
        data.setPatterns(collectMethodPatterns(testMethods));
        data.setScope(TestSearchScope.WHOLE_PROJECT);
    }

    /**
     * Collects method patterns from the given set of test methods.
     *
     * @param testMethods The set of test methods to collect patterns from.
     * @return A LinkedHashSet of method patterns.
     */
    private static LinkedHashSet<String> collectMethodPatterns(Set<PsiMethod> testMethods) {
        LinkedHashSet<String> methodPatterns = new LinkedHashSet<>();
        for (PsiMethod method : testMethods) {
            PsiClass psiClass = method.getContainingClass();
            if (psiClass != null) {
                String className = psiClass.getQualifiedName();
                String methodName = method.getName();
                if (className != null) {
                    methodPatterns.add(className + "," + methodName);
                }
            }
        }
        return methodPatterns;
    }

    /**
     * Builds the execution environment for running the tests.
     *
     * @param settings The RunnerAndConfigurationSettings.
     * @param latch    The CountDownLatch to synchronize the test run completion.
     * @return The built ExecutionEnvironment.
     */
    private static ExecutionEnvironment buildExecutionEnvironment(RunnerAndConfigurationSettings settings, CountDownLatch latch) {
        try {
            return ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), settings).build();
        } catch (ExecutionException e) {
            latch.countDown();
            throw new RuntimeException(e);
        }
    }

    /**
     * Starts the run profile for the specified execution environment.
     *
     * @param project     The IntelliJ project.
     * @param environment The ExecutionEnvironment.
     * @param latch       The CountDownLatch to synchronize the test run completion.
     */
    private static void startRunProfile(Project project, ExecutionEnvironment environment, CountDownLatch latch) {
        try {
            ExecutionManager.getInstance(project).startRunProfile(environment, state -> {
                try {
                    var handler = state.execute(environment.getExecutor(), environment.getRunner());
                    if (handler != null) {
                        handler.getProcessHandler().addProcessListener(new ProcessAdapter() {
                            @Override
                            public void processTerminated(@NotNull ProcessEvent event) {
                                latch.countDown(); // Release the latch when the process terminates
                            }
                        });
                        return new RunContentDescriptor(handler.getExecutionConsole(), handler.getProcessHandler(), handler.getExecutionConsole().getComponent(), "Run Tests");
                    } else {
                        latch.countDown(); // Release the latch if the process handler is null
                        throw new ExecutionException("Failed to start the run configuration.");
                    }
                } catch (ExecutionException e) {
                    latch.countDown(); // Ensure latch is released in case of exception
                    throw e;
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
