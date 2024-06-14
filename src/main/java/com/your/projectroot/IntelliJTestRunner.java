package com.your.projectroot;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

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
    public static void runTests(Project project, Set<PsiMethod> testMethods, @Nullable Runnable afterTestRun) {
        runTestsWithConfiguration(project, testMethods, "GlobalTestRunConfiguration",afterTestRun);
    }

    /**
     * Runs the specified set of JUnit test methods within the given IntelliJ project.
     *
     * @param project     The IntelliJ project in which to run the tests.
     * @param testMethods The set of test methods to be run.
     */
    public static void runTestsForPrevious(Project project, Set<PsiMethod> testMethods,@Nullable Runnable afterTestRun) {
        runTestsWithConfiguration(project, testMethods, "TestConfigForPrevious",afterTestRun);
    }

    private static void runTestsWithConfiguration(Project project, Set<PsiMethod> testMethods, String configName,@Nullable Runnable afterTestRun) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationType junitConfigType = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory junitConfigFactory = junitConfigType.getConfigurationFactories()[0];

        RunnerAndConfigurationSettings settings = runManager.createConfiguration(configName + System.currentTimeMillis(), junitConfigFactory);
        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

        ApplicationManager.getApplication().runReadAction(() -> {
            setupTestConfigurationData(configuration, testMethods);
        });

        configuration.setWorkingDirectory(project.getBasePath());

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);

        // Run the configuration and invoke callback after completion
        ApplicationManager.getApplication().invokeLater(() ->{
            ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
            if(afterTestRun!=null){
                afterTestRun.run();
            }
        });
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
