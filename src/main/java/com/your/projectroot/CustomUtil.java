package com.your.projectroot;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Utility class for various operations related to method signatures, class names, and PsiElements.
 */
public class CustomUtil {

    /**
     * Generates the signature of a method declaration given its signature and class name.
     *
     * @param signature The signature of the method.
     * @param className The name of the class declaring the method.
     * @return The full signature of the method in the format "className.signature".
     */
    public static String getSignOfMethodDeclaration(CallableDeclaration.Signature signature, String className) {
        return className + "." + signature.asString();
    }

    /**
     * Finds the name of the class declaring a given PsiElement.
     *
     * @param element The PsiElement whose declaring class is to be found.
     * @return The fully qualified name of the declaring class, or null if not found.
     */
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

    /**
     * Generates the method signature for a given PsiMethod and class name.
     *
     * @param method    The PsiMethod whose signature is to be generated.
     * @param className The name of the class declaring the method.
     * @return The full method signature in the format "className.methodName(parameterTypes)".
     */
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

    /**
     * Extracts the method name from a given method signature.
     *
     * @param methodSignature The full method signature.
     * @return The extracted method name.
     */
    public static String extractMethodName(String methodSignature) {
        int startIndex = methodSignature.lastIndexOf('.');
        int lastIndex = methodSignature.indexOf('(');
        if (startIndex != -1) {
            return methodSignature.substring(startIndex + 1, lastIndex);
        }
        return methodSignature;   // Fallback to the whole signature if parsing fails
    }

    /**
     * Extracts the parameter types from a given method signature.
     *
     * @param methodSignature The full method signature.
     * @return An array of parameter types as strings.
     */
    public static String[] extractParameterTypes(String methodSignature) {
        int startIndex = methodSignature.indexOf('(');
        int endIndex = methodSignature.indexOf(')');
        if (startIndex != -1 && endIndex != -1) {
            String params = methodSignature.substring(startIndex + 1, endIndex);
            return params.isEmpty() ? new String[0] : params.split(",\\s*");
        }
        return new String[0];  // Fallback to no parameters if parsing fails
    }

    /**
     * Checks if the parameter types of a given PsiMethod match the specified parameter types.
     *
     * @param method         The PsiMethod to be checked.
     * @param parameterTypes The expected parameter types.
     * @return True if the parameter types match, false otherwise.
     */
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

    /**
     * Extracts the class name from a given method signature.
     *
     * @param methodSignature The full method signature.
     * @return The extracted class name.
     */
    public static String extractClassName(String methodSignature) {
        int lastDotIndex = methodSignature.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return methodSignature.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * Checks if a given PsiMethod is a test method.
     *
     * @param method The PsiMethod to be checked.
     * @return True if the method is annotated with @Test, false otherwise.
     */
    public static boolean isTestMethod(PsiMethod method) {
        PsiAnnotation testAnnotation = method.getAnnotation("org.junit.jupiter.api.Test");
        return testAnnotation != null;
    }
}
