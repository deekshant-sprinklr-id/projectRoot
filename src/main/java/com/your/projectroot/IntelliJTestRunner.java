package com.your.projectroot;

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
import com.intellij.execution.ui.ExecutionConsole;
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
     */
    public static void runTests(Project project, Set<PsiMethod> testMethods, CountDownLatch latch) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationType junitConfigType = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory junitConfigFactory = junitConfigType.getConfigurationFactories()[0];

        RunnerAndConfigurationSettings settings = runManager.createConfiguration("First", junitConfigFactory);
        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

        ApplicationManager.getApplication().runReadAction(() -> {
            setupTestConfigurationData(configuration, testMethods);
        });

        configuration.setWorkingDirectory(project.getBasePath());

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);

//        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());

        ExecutionEnvironmentBuilder builder;
        try {
            System.out.println("Here1");
            builder = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), settings);
        } catch (ExecutionException e) {
            latch.countDown();
            throw new RuntimeException(e);
        }
        ExecutionEnvironment environment = builder.build();
        System.out.println("Here2");
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ExecutionManager.getInstance(project).startRunProfile(environment, (state) -> {
                    try {
                        var handler = state.execute(environment.getExecutor(), environment.getRunner());
                        if (handler != null) {
                            handler.getProcessHandler().addProcessListener(new ProcessAdapter() {
                                @Override
                                public void processTerminated(@NotNull ProcessEvent event) {
                                    latch.countDown(); // Release the latch when the process terminates
                                }
                            });
                            ExecutionConsole executionConsole = handler.getExecutionConsole();
                            var processHandler = handler.getProcessHandler();
                            var component = handler.getExecutionConsole().getComponent();
                            return new RunContentDescriptor(executionConsole, processHandler, component, "Run Tests");
                        } else {
                            latch.countDown(); // Release the latch if the process handler is null
                            throw new ExecutionException("Failed to start the run configuration.");
                        }
                    } catch (ExecutionException ex) {
                        latch.countDown(); // Ensure latch is released in case of exception
                        throw ex;
                    }
                });
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
//        System.out.println("This is called");
//        latch.countDown();
    }

    // Method for running tests on previous state
    public static void runTestsForPrevious(Project project, Set<PsiMethod> testMethods) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationType junitConfigType = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory junitConfigFactory = junitConfigType.getConfigurationFactories()[0];

        RunnerAndConfigurationSettings settings = runManager.createConfiguration("Second", junitConfigFactory);
        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();


        ApplicationManager.getApplication().runReadAction(() -> {
            setupTestConfigurationData(configuration,testMethods);
        });


        configuration.setWorkingDirectory(project.getBasePath());

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);

//        List<BeforeRunTask<?>> beforeRunTasks = settings.getConfiguration().getBeforeRunTasks();
//        System.out.println("Before Configured tasks for SecondTestRunConfiguration: " + beforeRunTasks);
//        BeforeRunTask<?> buildTask = beforeRunTasks.get(0);
//        beforeRunTasks.clear();
//        PopChangesBeforeRunTaskProvider.PopChangesBeforeRunTask popTask = new PopChangesBeforeRunTaskProvider.PopChangesBeforeRunTask();
//        beforeRunTasks.add(buildTask);
//        beforeRunTasks.add(popTask);
//       settings.getConfiguration().setBeforeRunTasks(beforeRunTasks);

//        System.out.println("Configured tasks for SecondTestRunConfiguration: " + beforeRunTasks);
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
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

        LinkedHashSet<String> methodPatterns = collectMethodPatterns(testMethods);

        data.setPatterns(methodPatterns);
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
                    String pattern = className + "," + methodName;
                    methodPatterns.add(pattern);
                    System.out.println("Added pattern: " + pattern); // DEBUG
                }
            }
        }
        return methodPatterns;
    }
}
