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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility class for running JUnit tests within an IntelliJ project.
 */
public class IntelliJTestRunner {

    /**
     * Runs the specified set of JUnit test methods within the given IntelliJ project.
     *
     * @param project    The IntelliJ project in which to run the tests.
     * @param testMethods The set of test methods to be run.
     */
    public static void runTests(Project project, Set<PsiMethod> testMethods) {
        RunManager runManager = RunManager.getInstance(project);

        ConfigurationType junitConfigType = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory junitConfigFactory = junitConfigType.getConfigurationFactories()[0];

        // Create a new configuration
        RunnerAndConfigurationSettings settings = runManager.createConfiguration("GlobalTestRunConfiguration", junitConfigFactory);
        JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

        JUnitConfiguration.Data data = configuration.getPersistentData();
        data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;

        // Collect all method patterns
        LinkedHashSet<String> methodPatterns = new LinkedHashSet<>();
        for (PsiMethod method : testMethods) {
            PsiClass psiClass = method.getContainingClass();
            if (psiClass != null) {
                String className = psiClass.getQualifiedName();
                String methodName = method.getName();
                if (className != null) {
                    String pattern = className + "," + methodName;
                    methodPatterns.add(pattern);
                    System.out.println("Added pattern: " + pattern); // Log the pattern
                }
            }
        }

        // Verify that class names and method names are properly captured
        if (methodPatterns.isEmpty()) {
            throw new IllegalArgumentException("No valid test methods found to run.");
        }

        // Set the test pattern
        data.setPatterns(methodPatterns);

        // Try different search scopes
        data.setScope(TestSearchScope.WHOLE_PROJECT);

        // Ensure working directory is set correctly
        configuration.setWorkingDirectory(project.getBasePath());

        // Add and select the configuration
        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);

        // Run the configuration automatically
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }
}
