package com.your.projectroot.Garbage;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.HashSet;
import java.util.Set;

public class CheckPublicMethodTest {

    public static Set<PsiMethod> findTestsForPublicMethods(Set<PsiMethod> affectedMethods) {
        Set<PsiMethod> associatedTests = new HashSet<>();
        Launcher launcher = LauncherFactory.create();

        for (PsiMethod method : affectedMethods) {
            if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
                String className = method.getContainingClass().getQualifiedName();
                String methodName = method.getName();

                LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectMethod(className, methodName))
                        .build();

                launcher.execute(request, new TestExecutionListener() {
                    @Override
                    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
                        if (testExecutionResult.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                            associatedTests.add(method);
                        }
                    }
                });
            }
        }
        return associatedTests;
    }
}
