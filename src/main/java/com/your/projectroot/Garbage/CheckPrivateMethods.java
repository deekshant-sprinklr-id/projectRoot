package com.your.projectroot.Garbage;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;

import java.util.HashSet;
import java.util.Set;

public class CheckPrivateMethods {

    public static Set<PsiMethod> findTestsForPrivateMethods(Set<PsiMethod> affectedMethods, Project project) {
        Set<PsiMethod> associatedTests = new HashSet<>();

        for (PsiMethod method : affectedMethods) {
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                    PsiMethod testMethod = findTestForPrivateMethod(method, project);
                    if (testMethod != null) {
                        associatedTests.add(testMethod);
                    }
                }
            }
        }
        return associatedTests;
    }

    private static PsiMethod findTestForPrivateMethod(PsiMethod method, Project project) {
        Set<PsiMethod> potentialTests = findAllPotentialTestMethods(project);

        for (PsiMethod testMethod : potentialTests) {
            if (doesTestMethodUsePrivateMethod(testMethod, method)) {
                return testMethod;
            }
        }
        return null;
    }

    private static boolean doesTestMethodUsePrivateMethod(PsiMethod testMethod, PsiMethod privateMethod) {
        PsiCodeBlock body = testMethod.getBody();
        if (body != null) {
            for (PsiStatement statement : body.getStatements()) {
                if (statement.getText().contains(privateMethod.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<PsiMethod> findAllPotentialTestMethods(Project project) {
        Set<PsiMethod> testMethods = new HashSet<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        for (String className : shortNamesCache.getAllClassNames()) {
            for (PsiClass psiClass : shortNamesCache.getClassesByName(className, scope)) {
                for (PsiMethod method : psiClass.getMethods()) {
                    if (isTestMethod(method)) {
                        testMethods.add(method);
                    }
                }
            }
        }
        return testMethods;
    }

    private static boolean isTestMethod(PsiMethod method) {
        PsiAnnotation testAnnotation = method.getAnnotation("org.junit.jupiter.api.Test");
        return testAnnotation != null;
    }
}
