//package com.your.projectroot;
//import com.intellij.execution.Executor;
//import com.intellij.execution.ProgramRunnerUtil;
//import com.intellij.execution.RunManager;
//import com.intellij.execution.RunnerAndConfigurationSettings;
//import com.intellij.execution.actions.ConfigurationContext;
//import com.intellij.execution.executors.DefaultRunExecutor;
//import com.intellij.execution.junit.JUnitConfiguration;
//import com.intellij.execution.junit.JUnitConfigurationType;
//import com.intellij.openapi.module.Module;
//import com.intellij.openapi.module.ModuleUtilCore;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.PsiMethod;
//import com.intellij.psi.util.PsiUtil;
//
//import java.util.List;
//import java.util.Set;
//
//public class TestRunner {
//
//    private final Project project;
//
//    public TestRunner(Project project) {
//        this.project = project;
//    }
//
//    public void runTestMethods(Set<PsiMethod> testMethods) {
//        RunManager runManager = RunManager.getInstance(project);
//        JUnitConfigurationType configurationType = JUnitConfigurationType.getInstance();
//
//        for (PsiMethod testMethod : testMethods) {
//            RunnerAndConfigurationSettings settings = runManager.createConfiguration(testMethod.getName(), configurationType.getConfigurationFactories()[0]);
//            JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();
//
//            configuration.setModule(ModuleUtilCore.findModuleForPsiElement(testMethod));
//            configuration.bePatternConfiguration(List.of(testMethod.getContainingClass().getQualifiedName() + "#" + testMethod.getName()), null);
//
//            ConfigurationContext context = ConfigurationContext.getFromContext(testMethod);
//            configuration.setGeneratedName();
//            runManager.addConfiguration(settings);
//            runManager.setSelectedConfiguration(settings);
//
//            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
//            ProgramRunnerUtil.executeConfiguration(settings, executor);
//        }
//    }
//}
//
