package com.your.projectroot;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class CustomUtil {

    public static String getSignOfMethodDeclaration(CallableDeclaration.Signature signature, String className) {
        return className + "." + signature.asString();
    }

    public static String findDeclaringClassName(PsiElement element) {
        PsiReference reference = element.getReference();
        if (reference != null) {
            PsiElement resolvedElement = reference.resolve();
            if (resolvedElement != null) {
                PsiClass declaringClass = PsiTreeUtil.getParentOfType(resolvedElement, PsiClass.class);
                if (declaringClass != null) {
                    return declaringClass.getQualifiedName();
                }
            }
        }
        return null;
    }

    public static String getMethodSignatureForPsiElement(PsiMethod method, String className) {
        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            signatureBuilder.append(parameters[i].getType().getPresentableText());
            if (i < parameters.length - 1) {
                signatureBuilder.append(", ");
            }
        }
        signatureBuilder.append(')');
        return className + "." + signatureBuilder;
    }

    public static String extractMethodName(String methodSignature) {
        int startIndex = methodSignature.lastIndexOf('.');
        int lastIndex = methodSignature.indexOf('(');
        if (startIndex != -1) {
            return methodSignature.substring(startIndex + 1, lastIndex);
        }
        return methodSignature;   // Fallback to the whole signature if parsing fails
    }

    public static String[] extractParameterTypes(String methodSignature) {
        // Assuming the format "methodName(parameters)"
        int startIndex = methodSignature.indexOf('(');
        int endIndex = methodSignature.indexOf(')');
        if (startIndex != -1 && endIndex != -1) {
            String params = methodSignature.substring(startIndex + 1, endIndex);
            return params.isEmpty() ? new String[0] : params.split(",\\s*");
        }
        return new String[0];  // Fallback to no parameters if parsing fails
    }

    public static boolean isMatchingParameters(PsiMethod method, String[] parameterTypes) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].getType().getPresentableText().equals(parameterTypes[i])) {
                return false;
            }
        }
        return true;
    }

    public static String extractClassName(String methodSignature) {
        int lastDotIndex = methodSignature.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return methodSignature.substring(0, lastDotIndex);
        }
        return "";
    }

    public static boolean isTestMethod(PsiMethod method) {
        PsiAnnotation testAnnotation = method.getAnnotation("org.junit.jupiter.api.Test");
        return testAnnotation != null;
    }

}
