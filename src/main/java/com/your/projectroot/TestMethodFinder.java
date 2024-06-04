package com.your.projectroot;
import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashSet;
import java.util.Set;

public class TestMethodFinder {

    private final Project project;

    public TestMethodFinder(Project project) {
        this.project = project;
    }

    public Set<PsiMethod> findAssociatedTestMethods(Set<PsiMethod> methods) {
        Set<PsiMethod> testMethods = new HashSet<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        for (PsiMethod method : methods) {
            Set<PsiMethod> callingMethods = new HashSet<>();
            findCallingMethods(method, callingMethods, scope);

            for (PsiMethod callingMethod : callingMethods) {
                if (isTestMethod(callingMethod)) {
                    testMethods.add(callingMethod);
                }
            }
        }

        return testMethods;
    }

    private void findCallingMethods(PsiMethod method, Set<PsiMethod> callingMethods, GlobalSearchScope scope) {
        MethodReferencesSearch.search(method).forEach(reference -> {
            PsiMethod callingMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
            if (callingMethod != null && !callingMethods.contains(callingMethod)) {
                callingMethods.add(callingMethod);
                findCallingMethods(callingMethod, callingMethods, scope);
            }
            return true;
        });
    }

    private boolean isTestMethod(PsiMethod method) {
        return JUnitUtil.isTestMethod((Location<? extends PsiMethod>) method);
    }
}

