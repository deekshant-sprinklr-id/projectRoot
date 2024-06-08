package com.your.projectroot;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class PrivateMethodUsageFinder {

    public static Set<PsiMethod> findPrivateMethodUsages(Project project,Set<PsiMethod> privateMethods) {
        Set<PsiMethod> testMethodsUsingPrivateMethods = new HashSet<>();
        GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);

        PsiClass testAnnotation = JavaPsiFacade.getInstance(project).findClass("org.junit.jupiter.api.Test", GlobalSearchScope.allScope(project));

        if (testAnnotation == null) {
            return testMethodsUsingPrivateMethods; // No @Test annotation found in the project
        }

        AnnotatedElementsSearch.searchPsiMethods(testAnnotation, searchScope).forEach(method -> {

            if (usesReflectionToAccessPrivateMethods(method) && checkForPrivateMethod(method, privateMethods)) {
                System.out.println(method);
                System.out.println(testAnnotation);
                testMethodsUsingPrivateMethods.add(method);
            }
            return true;
        });

        return testMethodsUsingPrivateMethods;
    }

    private static boolean usesReflectionToAccessPrivateMethods(PsiMethod method) {
        final boolean[] usesReflection = {false};

        method.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiMethod resolvedMethod = expression.resolveMethod();
                String methodName = resolvedMethod != null ? resolvedMethod.getName() : null;
                if ("setAccessible".equals(methodName)) {
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    if (arguments.length == 1 && arguments[0] instanceof PsiLiteralExpression literalExpression) {
                        Object value = literalExpression.getValue();
                        if (value instanceof Boolean) {
                            usesReflection[0]=true;
                        }
                    }
                }
                else if("getDeclaredMethod".equals(methodName)){
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    if (arguments.length == 1 && arguments[0] instanceof PsiLiteralExpression literalExpression) {
                        Object value = literalExpression.getValue();
                        if (value instanceof String) {
                            usesReflection[0]=true;
                        }
                    }
                }
                else if("getDeclaredMethods".equals(methodName)){
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    if (arguments.length == 0) {
                        usesReflection[0]=true;
                    }
                }
            }
        });

        return usesReflection[0];
    }

    private static boolean checkForPrivateMethod(PsiMethod method, Set<PsiMethod> privateMethods) {
        HashMap<PsiMethod,String> privateMethodNames = new HashMap<>();
        for (PsiMethod privateMethod : privateMethods) {
            privateMethodNames.put(privateMethod,privateMethod.getName());
        }
        final boolean[] usesMethod = {false};

        method.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiMethod resolvedMethod = expression.resolveMethod();
                String methodName = resolvedMethod != null ? resolvedMethod.getName() : null;
                if("equals".equals(methodName)){
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    if(arguments[0].textContains('"')){
                        String privateMethod = CustomUtil.getCallingMethodName(arguments[0].getText());
                        if(privateMethodNames.containsValue(privateMethod)){
                            usesMethod[0] = true;
                        }
                    }
                    else{
                        String qualifiedName = expression.getMethodExpression().getQualifiedName();
                        String privateMethod = CustomUtil.getCallingMethodName(qualifiedName);
                        if(privateMethodNames.containsValue(privateMethod)){
                            usesMethod[0] = true;
                        }
                    }
                }else if("getDeclaredMethod".equals(methodName)){
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    String privateMethod = CustomUtil.getCallingMethodName(arguments[0].getText());
                    if(privateMethodNames.containsValue(privateMethod)){
                        usesMethod[0] = true;
                    }
                }
            }
        });
        return usesMethod[0];
    }
}
