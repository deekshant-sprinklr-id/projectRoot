package com.your.projectroot;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;

import java.util.Collection;

public class UsageFinder {

    public static void printMethodUsages(PsiMethod method) {
        Collection<PsiReference> references = ReferencesSearch.search(method).findAll();
        System.out.println("Usages of method " + method.getName() + ":");
        for (PsiReference reference : references) {
            System.out.println("  " + reference.getElement().getContainingFile().getVirtualFile().getPath() +
                    " at line " + reference.getElement().getTextOffset());
        }
    }
}

