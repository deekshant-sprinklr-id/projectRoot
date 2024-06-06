package com.your.projectroot;

public class SignatureToPsiMethod{





    private String extractMethodName(String methodSignature) {
        int startIndex = methodSignature.lastIndexOf('.') + 1;
        int endIndex = methodSignature.indexOf('(');
        if (startIndex != -1 && endIndex != -1) {
            return methodSignature.substring(startIndex, endIndex);
        }
        return "";
    }


}
