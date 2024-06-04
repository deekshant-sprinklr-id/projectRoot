package com.your.projectroot;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service(Service.Level.PROJECT)
public final class ChangeTrackingService {

    private static final Logger logger = Logger.getInstance(ChangeTrackingService.class);
    private final Project project;
    public static List<String> changes = new ArrayList<>();
    private final List<Map<String,Integer>> affectedMethods = new ArrayList<>();


    public ChangeTrackingService(Project project) {
        this.project = project;
    }

    public void trackChangesAndRunTests(int maxDepth) {
        System.out.println("Beginning: "+maxDepth);
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);

        // Get the list of local changes
        List<LocalChangeList> changes = changeListManager.getChangeLists();

        // Iterate over changes and process them
        for (LocalChangeList changeList : changes) {
            for (Change change : changeList.getChanges()) {
                VirtualFile file = change.getVirtualFile();

                if (file != null) {
                    // Process each changed file to determine affected unit tests
                    String sourceFilePath = file.getPath();
                    System.out.println("Changed Class: " + sourceFilePath);
                    // Get old and new content of the file
                    String oldContent = null;
                    try {
                        oldContent = getOldFileContent(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String newContent = getNewFileContent(file);
                    if (oldContent != null && newContent != null) {
                        // Parse old and new contents
                        JavaParser parser =  new JavaParser();
                        CompilationUnit oldCompilationUnit;
                        oldCompilationUnit = parser.parse(new ByteArrayInputStream(oldContent.getBytes())).getResult().orElse(null);
                        CompilationUnit newCompilationUnit;
                        newCompilationUnit = parser.parse(new ByteArrayInputStream(newContent.getBytes())).getResult().orElse(null);

                        // Compare methods
                        compareMethods(oldCompilationUnit, newCompilationUnit);
                    }
                } else {
                    System.out.println("File is null");
                }
            }
        }
        //identifyAffectedMethods();

        findMethodUsages(maxDepth);

        for(Map<String,Integer> str: affectedMethods){
            System.out.println(str);
        }
    }

    private String getOldFileContent(VirtualFile file) throws IOException {
        String projectBasePath = project.getBasePath();
        File repoDir = new File(projectBasePath);
        String absoluteFilePath = file.getPath();
        String relativeFilePath = absoluteFilePath.substring(projectBasePath.length() + 1); // Convert to relative path

        try (Git git = Git.open(repoDir)) {
            Repository repository = git.getRepository();

            // Get the head commit
            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                throw new IOException("Couldn't resolve HEAD");
            }

            // Get the tree of the head commit
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit headCommit = revWalk.parseCommit(headId);
                ObjectId treeId = headCommit.getTree().getId();

                // Find the specified file in the tree
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(treeId);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(relativeFilePath));

                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        if (path.equals(relativeFilePath)) {
                            // Get the file content
                            ObjectId objectId = treeWalk.getObjectId(0);
                            try (ObjectReader objectReader = repository.newObjectReader()) {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                objectReader.open(objectId).copyTo(outputStream);
                                return outputStream.toString(StandardCharsets.UTF_8.name());
                            }
                        }
                    }

                    throw new IOException("File not found in the HEAD commit: " + relativeFilePath);
                }
            }
        }
    }


    private String getNewFileContent(VirtualFile file) {
        try {
            byte[] content = file.contentsToByteArray();
            return new String(content);
        } catch (IOException e) {
            throw new RuntimeException("Error reading new file content", e);
        }
    }

    private void compareMethods(CompilationUnit oldCompilationUnit, CompilationUnit newCompilationUnit) {
        List<MethodDeclaration> oldMethods = new ArrayList<>();
        List<MethodDeclaration> newMethods = new ArrayList<>();
        oldCompilationUnit.accept(new MethodVisitor(), oldMethods);
        newCompilationUnit.accept(new MethodVisitor(), newMethods);

        //Commented out code to print all the methods (New and Old)
//        for (MethodDeclaration m1 : oldMethods) System.out.println(m1.getName());
//        for (MethodDeclaration m2 : newMethods) System.out.println(m2.getName());

        Map<String, MethodDeclaration> oldMethodsMap = new HashMap<>();
        for (MethodDeclaration method : oldMethods) {
            oldMethodsMap.put(getMethodSignature(method), method);
        }

        for (MethodDeclaration newMethod : newMethods) {
            //String methodName = newMethod.getName();
            if (!oldMethodsMap.containsKey(getMethodSignature(newMethod))) {
                System.out.println("Method added: " + newMethod.getName().asString());
                changes.add(getMethodSignature(newMethod));
            } else {
                MethodDeclaration oldMethod = oldMethodsMap.get(getMethodSignature(newMethod));
                if (!oldMethod.getBody().equals(newMethod.getBody())) {
                    System.out.println("Method changed: " + newMethod.getName().asString());
                    changes.add(getMethodSignature(newMethod));
                }
                oldMethodsMap.remove(getMethodSignature(newMethod));
            }
        }

        for (String removedMethodName : oldMethodsMap.keySet()) {
            System.out.println("Method removed: " + removedMethodName);
            changes.add(removedMethodName);
        }
    }

    private void findMethodUsages(int maxDepth) {
        for(String c:changes){
            System.out.println(c);
        }
        for (String methodName : changes) {
            System.out.println("Here");
            findUsagesForMethod(methodName,maxDepth,0,new HashSet<>());
        }
    }

    private void findUsagesForMethod(String methodSignature, int maxDepth, int currentDepth,Set<String> visited) {

        if (currentDepth > maxDepth) {
            return;
        }
        if(visited.contains(methodSignature)){
            System.out.println("Cycle detected at: "+methodSignature);
            return;
        }
        visited.add(methodSignature);
        boolean methodExistsAtLowerDepth=false;
        // Check if the method is already in the affectedMethods list and if the current depth is less than or equal to the stored depth
        for(Map<String,Integer> mp:affectedMethods){
            if(mp.containsKey(methodSignature) && mp.get(methodSignature)<=currentDepth){
                methodExistsAtLowerDepth=true;
                break;
            }
        }
        if (methodExistsAtLowerDepth) {
            return;
        }

        // Add the method to the affectedMethods list with the current depth
        affectedMethods.add(Map.of(methodSignature,currentDepth));

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        String methodName = extractMethodName(methodSignature);
        String[] parameterTypes = extractParameterTypes(methodSignature);
        PsiMethod[] methods = shortNamesCache.getMethodsByName(methodName, scope);

        for (PsiMethod method : methods) {
            if (isMatchingParameters(method, parameterTypes)) {
                Collection<PsiReference> references = ReferencesSearch.search(method, scope).findAll();
                for (PsiReference reference : references) {
                    PsiElement element = reference.getElement();
                    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                    if (containingMethod != null) {
                        String containingMethodSignature = getMethodSignature(containingMethod);
                        System.out.println("Method " + methodSignature + " is used in: " + containingMethodSignature);

                        // If the reference is a method call, continue DFS traversal
                        System.out.println("Traverse for: " + containingMethodSignature);
                        findUsagesForMethod(containingMethodSignature, maxDepth, currentDepth + 1,visited);
                    }
                }
            }
        }
        // Remove from visited set
        visited.remove(methodSignature);
    }


    private String getMethodSignature(MethodDeclaration method) {
        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append(method.getName()).append('(');
        List<com.github.javaparser.ast.body.Parameter> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            signatureBuilder.append(parameters.get(i).getType());
            if (i < parameters.size() - 1) {
                signatureBuilder.append(", ");
            }
        }
        signatureBuilder.append(')');
        return signatureBuilder.toString();
    }

    private String getMethodSignature(PsiMethod method) {
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
        return signatureBuilder.toString();
    }

    private String extractMethodName(String methodSignature) {
        int startIndex = methodSignature.indexOf('(');
        if (startIndex != -1) {
            return methodSignature.substring(0, startIndex);
        }
        return methodSignature;   // Fallback to the whole signature if parsing fails
    }

    private String[] extractParameterTypes(String methodSignature) {
        // Assuming the format "methodName(parameters)"
        int startIndex = methodSignature.indexOf('(');
        int endIndex = methodSignature.indexOf(')');
        if (startIndex != -1 && endIndex != -1) {
            String params = methodSignature.substring(startIndex + 1, endIndex);
            return params.isEmpty() ? new String[0] : params.split(",\\s*");
        }
        return new String[0];  // Fallback to no parameters if parsing fails
    }

    private boolean isMatchingParameters(PsiMethod method, String[] parameterTypes) {
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

    private static class MethodVisitor extends VoidVisitorAdapter<List<MethodDeclaration>> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<MethodDeclaration> collector) {
            super.visit(classOrInterfaceDeclaration, collector);
            classOrInterfaceDeclaration.getMembers().forEach(member -> {
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) member;
                    collector.add(method);
                }
            });
        }
    }

    private void runAffectedTests(Project project, VirtualFile file) {
        // Placeholder method to run affected tests
        // Implement logic to determine and run affected tests
        logger.info("Running affected tests for: " + file.getPath());

        // Example: Execute tests (this part needs implementation based on your testing framework)
        // For instance, if using JUnit:
        // TestExecutor.executeTests(file);
    }
}

//package com.your.projectroot;
//
//import com.github.javaparser.JavaParser;
//import com.github.javaparser.ast.CompilationUnit;
//import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
//import com.github.javaparser.ast.body.MethodDeclaration;
//import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
//import com.intellij.openapi.components.Service;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.vcs.changes.Change;
//import com.intellij.openapi.vcs.changes.ChangeListManager;
//import com.intellij.openapi.vcs.changes.LocalChangeList;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.PsiElement;
//import com.intellij.psi.PsiMethod;
//import com.intellij.psi.PsiParameter;
//import com.intellij.psi.PsiReference;
//import com.intellij.psi.search.GlobalSearchScope;
//import com.intellij.psi.search.PsiShortNamesCache;
//import com.intellij.psi.search.searches.ReferencesSearch;
//import com.intellij.psi.util.PsiTreeUtil;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.lib.ObjectId;
//import org.eclipse.jgit.lib.ObjectReader;
//import org.eclipse.jgit.lib.Repository;
//import org.eclipse.jgit.revwalk.RevCommit;
//import org.eclipse.jgit.revwalk.RevWalk;
//import org.eclipse.jgit.treewalk.TreeWalk;
//import org.eclipse.jgit.treewalk.filter.PathFilter;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.*;
//
//@Service(Service.Level.PROJECT)
//public final class ChangeTrackingService {
//
//    private static final Logger logger = Logger.getInstance(ChangeTrackingService.class);
//    private final Project project;
//    public static List<String> changes = new ArrayList<>();
//    private final Set<String> affectedMethods = new HashSet<>();
//
//    public ChangeTrackingService(Project project) {
//        this.project = project;
//    }
//
//    public void trackChangesAndRunTests(int maxDepth) {
//        System.out.println("Beginning: " + maxDepth);
//        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
//
//        // Get the list of local changes
//        List<LocalChangeList> changes = changeListManager.getChangeLists();
//
//        // Iterate over changes and process them
//        for (LocalChangeList changeList : changes) {
//            for (Change change : changeList.getChanges()) {
//                VirtualFile file = change.getVirtualFile();
//
//                if (file != null) {
//                    // Process each changed file to determine affected unit tests
//                    String sourceFilePath = file.getPath();
//                    System.out.println("Changed Class: " + sourceFilePath);
//                    // Get old and new content of the file
//                    String oldContent = null;
//                    try {
//                        oldContent = getOldFileContent(file);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    String newContent = getNewFileContent(file);
//                    if (oldContent != null && newContent != null) {
//                        // Parse old and new contents
//                        JavaParser parser = new JavaParser();
//                        CompilationUnit oldCompilationUnit;
//                        oldCompilationUnit = parser.parse(new ByteArrayInputStream(oldContent.getBytes())).getResult().orElse(null);
//                        CompilationUnit newCompilationUnit;
//                        newCompilationUnit = parser.parse(new ByteArrayInputStream(newContent.getBytes())).getResult().orElse(null);
//
//                        // Compare methods
//                        compareMethods(oldCompilationUnit, newCompilationUnit);
//                    }
//                } else {
//                    System.out.println("File is null");
//                }
//            }
//        }
//        identifyAffectedMethods();
//        runAssociatedTests(maxDepth);
//    }
//
//    private String getOldFileContent(VirtualFile file) throws IOException {
//        String projectBasePath = project.getBasePath();
//        File repoDir = new File(projectBasePath);
//        String absoluteFilePath = file.getPath();
//        String relativeFilePath = absoluteFilePath.substring(projectBasePath.length() + 1); // Convert to relative path
//
//        try (Git git = Git.open(repoDir)) {
//            Repository repository = git.getRepository();
//
//            // Get the head commit
//            ObjectId headId = repository.resolve("HEAD");
//            if (headId == null) {
//                throw new IOException("Couldn't resolve HEAD");
//            }
//
//            // Get the tree of the head commit
//            try (RevWalk revWalk = new RevWalk(repository)) {
//                RevCommit headCommit = revWalk.parseCommit(headId);
//                ObjectId treeId = headCommit.getTree().getId();
//
//                // Find the specified file in the tree
//                try (TreeWalk treeWalk = new TreeWalk(repository)) {
//                    treeWalk.addTree(treeId);
//                    treeWalk.setRecursive(true);
//                    treeWalk.setFilter(PathFilter.create(relativeFilePath));
//
//                    while (treeWalk.next()) {
//                        String path = treeWalk.getPathString();
//                        if (path.equals(relativeFilePath)) {
//                            // Get the file content
//                            ObjectId objectId = treeWalk.getObjectId(0);
//                            try (ObjectReader objectReader = repository.newObjectReader()) {
//                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                                objectReader.open(objectId).copyTo(outputStream);
//                                return outputStream.toString(StandardCharsets.UTF_8.name());
//                            }
//                        }
//                    }
//
//                    throw new IOException("File not found in the HEAD commit: " + relativeFilePath);
//                }
//            }
//        }
//    }
//
//    private String getNewFileContent(VirtualFile file) {
//        try {
//            byte[] content = file.contentsToByteArray();
//            return new String(content);
//        } catch (IOException e) {
//            throw new RuntimeException("Error reading new file content", e);
//        }
//    }
//
//    private void compareMethods(CompilationUnit oldCompilationUnit, CompilationUnit newCompilationUnit) {
//        List<MethodDeclaration> oldMethods = new ArrayList<>();
//        List<MethodDeclaration> newMethods = new ArrayList<>();
//        oldCompilationUnit.accept(new MethodVisitor(), oldMethods);
//        newCompilationUnit.accept(new MethodVisitor(), newMethods);
//
//        Map<String, MethodDeclaration> oldMethodsMap = new HashMap<>();
//        for (MethodDeclaration method : oldMethods) {
//            oldMethodsMap.put(getMethodSignature(method), method);
//        }
//
//        for (MethodDeclaration newMethod : newMethods) {
//            String newMethodSignature = getMethodSignature(newMethod);
//            if (!oldMethodsMap.containsKey(newMethodSignature)) {
//                System.out.println("Method added: " + newMethod.getName());
//                changes.add(newMethodSignature);
//            } else {
//                MethodDeclaration oldMethod = oldMethodsMap.get(newMethodSignature);
//                if (!oldMethod.getBody().equals(newMethod.getBody())) {
//                    System.out.println("Method changed: " + newMethod.getName());
//                    changes.add(newMethodSignature);
//                }
//                oldMethodsMap.remove(newMethodSignature);
//            }
//        }
//
//        for (String removedMethodName : oldMethodsMap.keySet()) {
//            System.out.println("Method removed: " + removedMethodName);
//            changes.add(removedMethodName);
//        }
//    }
//
//    private void identifyAffectedMethods() {
//        Set<String> visited = new HashSet<>();
//        Queue<String> queue = new LinkedList<>(changes);
//        while (!queue.isEmpty()) {
//            String method = queue.poll();
//            if (!visited.contains(method)) {
//                visited.add(method);
//                affectedMethods.add(method);
//            }
//        }
//        for (String i : affectedMethods) {
//            System.out.println(i);
//        }
//    }
//
//    private void runAssociatedTests(int maxDepth) {
//        Set<PsiMethod> psiMethods = new HashSet<>();
//        for (String methodSignature : affectedMethods) {
//            psiMethods.add(findPsiMethod(methodSignature));
//        }
//
//        TestMethodFinder testMethodFinder = new TestMethodFinder(project);
//        Set<PsiMethod> testMethods = testMethodFinder.findAssociatedTestMethods(psiMethods);
//
//        TestRunner testRunner = new TestRunner(project);
//        testRunner.runTestMethods(testMethods);
//    }
//
//    private PsiMethod findPsiMethod(String methodSignature) {
//        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
//        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);
//        String methodName = extractMethodName(methodSignature);
//        String[] parameterTypes = extractParameterTypes(methodSignature);
//        PsiMethod[] methods = shortNamesCache.getMethodsByName(methodName, scope);
//
//        for (PsiMethod method : methods) {
//            if (isMatchingParameters(method, parameterTypes)) {
//                return method;
//            }
//        }
//        return null;
//    }
//
//    private String getMethodSignature(MethodDeclaration method) {
//        StringBuilder signatureBuilder = new StringBuilder();
//        signatureBuilder.append(method.getName()).append('(');
//        List<com.github.javaparser.ast.body.Parameter> parameters = method.getParameters();
//        for (int i = 0; i < parameters.size(); i++) {
//            signatureBuilder.append(parameters.get(i).getType());
//            if (i < parameters.size() - 1) {
//                signatureBuilder.append(", ");
//            }
//        }
//        signatureBuilder.append(')');
//        return signatureBuilder.toString();
//    }
//
//    private String getMethodSignature(PsiMethod method) {
//        StringBuilder signatureBuilder = new StringBuilder();
//        signatureBuilder.append(method.getName()).append('(');
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        for (int i = 0; i < parameters.length; i++) {
//            signatureBuilder.append(parameters[i].getType().getPresentableText());
//            if (i < parameters.length - 1) {
//                signatureBuilder.append(", ");
//            }
//        }
//        signatureBuilder.append(')');
//        return signatureBuilder.toString();
//    }
//
//    private String extractMethodName(String methodSignature) {
//        int startIndex = methodSignature.indexOf('(');
//        if (startIndex != -1) {
//            return methodSignature.substring(0, startIndex);
//        }
//        return methodSignature; // Fallback to the whole signature if parsing fails
//    }
//
//    private String[] extractParameterTypes(String methodSignature) {
//        // Assuming the format "methodName(parameters)"
//        int startIndex = methodSignature.indexOf('(');
//        int endIndex = methodSignature.indexOf(')');
//        if (startIndex != -1 && endIndex != -1) {
//            String params = methodSignature.substring(startIndex + 1, endIndex);
//            return params.isEmpty() ? new String[0] : params.split(",\\s*");
//        }
//        return new String[0]; // Fallback to no parameters if parsing fails
//    }
//
//    private boolean isMatchingParameters(PsiMethod method, String[] parameterTypes) {
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        if (parameters.length != parameterTypes.length) {
//            return false;
//        }
//        for (int i = 0; i < parameters.length; i++) {
//            if (!parameters[i].getType().getPresentableText().equals(parameterTypes[i])) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    private static class MethodVisitor extends VoidVisitorAdapter<List<MethodDeclaration>> {
//        @Override
//        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<MethodDeclaration> collector) {
//            super.visit(classOrInterfaceDeclaration, collector);
//            classOrInterfaceDeclaration.getMembers().forEach(member -> {
//                if (member instanceof MethodDeclaration) {
//                    MethodDeclaration method = (MethodDeclaration) member;
//                    collector.add(method);
//                }
//            });
//        }
//    }
//
//    private void runAffectedTests(Project project, VirtualFile file) {
//        // Placeholder method to run affected tests
//        // Implement logic to determine and run affected tests
//        logger.info("Running affected tests for: " + file.getPath());
//
//        // Example: Execute tests (this part needs implementation based on your testing framework)
//        // For instance, if using JUnit:
//        // TestExecutor.executeTests(file);
//    }
//}



//package com.your.projectroot;
//
//import com.github.javaparser.JavaParser;
//import com.github.javaparser.ast.CompilationUnit;
//import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
//import com.github.javaparser.ast.body.MethodDeclaration;
//import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
//import com.intellij.openapi.components.Service;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.vcs.changes.Change;
//import com.intellij.openapi.vcs.changes.ChangeListManager;
//import com.intellij.openapi.vcs.changes.LocalChangeList;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.*;
//import com.intellij.psi.search.GlobalSearchScope;
//import com.intellij.psi.search.PsiShortNamesCache;
//import com.intellij.psi.search.searches.ReferencesSearch;
//import com.intellij.psi.util.PsiTreeUtil;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.lib.ObjectId;
//import org.eclipse.jgit.lib.ObjectReader;
//import org.eclipse.jgit.lib.Repository;
//import org.eclipse.jgit.revwalk.RevCommit;
//import org.eclipse.jgit.revwalk.RevWalk;
//import org.eclipse.jgit.treewalk.TreeWalk;
//import org.eclipse.jgit.treewalk.filter.PathFilter;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.*;
//
//@Service(Service.Level.PROJECT)
//public final class ChangeTrackingService {
//
//    private static final Logger logger = Logger.getInstance(ChangeTrackingService.class);
//    private final Project project;
//    public static List<String> changes = new ArrayList<>();
//    private final Set<String> affectedMethods = new HashSet<>();
//
//    public ChangeTrackingService(Project project) {
//        this.project = project;
//    }
//
//    public void trackChangesAndRunTests(int maxDepth) {
//        System.out.println("Beginning: ");
//        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
//
//        // Get the list of local changes
//        List<LocalChangeList> changes = changeListManager.getChangeLists();
//
//        // Iterate over changes and process them
//        for (LocalChangeList changeList : changes) {
//            for (Change change : changeList.getChanges()) {
//                VirtualFile file = change.getVirtualFile();
//
//                if (file != null) {
//                    // Process each changed file to determine affected unit tests
//                    String sourceFilePath = file.getPath();
//                    System.out.println("Changed Class: " + sourceFilePath);
//                    // Get old and new content of the file
//                    String oldContent = null;
//                    try {
//                        oldContent = getOldFileContent(file);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    String newContent = getNewFileContent(file);
//                    if (oldContent != null && newContent != null) {
//                        // Parse old and new contents
//                        JavaParser parser = new JavaParser();
//                        CompilationUnit oldCompilationUnit;
//                        oldCompilationUnit = parser.parse(new ByteArrayInputStream(oldContent.getBytes())).getResult().orElse(null);
//                        CompilationUnit newCompilationUnit;
//                        newCompilationUnit = parser.parse(new ByteArrayInputStream(newContent.getBytes())).getResult().orElse(null);
//
//                        // Compare methods
//                        compareMethods(oldCompilationUnit, newCompilationUnit);
//                    }
//                } else {
//                    System.out.println("File is null");
//                }
//            }
//        }
//        identifyAffectedMethods(maxDepth);
//        printAffectedMethods();
//    }
//
//    private void identifyAffectedMethods(int maxDepth) {
//        Set<String> visitedMethods = new HashSet<>();
//        Queue<String> methodQueue = new LinkedList<>(changes);
//
//        while (!methodQueue.isEmpty()) {
//            String methodSignature = methodQueue.poll();
//            if (!visitedMethods.contains(methodSignature)) {
//                visitedMethods.add(methodSignature);
//                affectedMethods.add(methodSignature);
//                performDFSTraversal(methodSignature, maxDepth, 0, visitedMethods);
//            }
//        }
//    }
//
//    private void performDFSTraversal(String methodSignature, int maxDepth, int currentDepth, Set<String> visitedMethods) {
//        if (currentDepth > maxDepth) {
//            return;
//        }
//
//        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
//        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);
//        String methodName = extractMethodName(methodSignature);
//        String[] parameterTypes = extractParameterTypes(methodSignature);
//        PsiMethod[] methods = shortNamesCache.getMethodsByName(methodName, scope);
//
//        for (PsiMethod method : methods) {
//            if (isMatchingParameters(method, parameterTypes)) {
//                Collection<PsiReference> references = ReferencesSearch.search(method, scope).findAll();
//                for (PsiReference reference : references) {
//                    System.out.println("Method " + methodName + " is used in: " + reference.getElement().getContainingFile().getVirtualFile().getPath());
//
//                    PsiElement element = reference.getElement();
//                    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
//                    if (containingMethod != null) {
//                        String containingMethodSignature = getMethodSignature(containingMethod);
//                        if (!visitedMethods.contains(containingMethodSignature)) {
//                            visitedMethods.add(containingMethodSignature);
//                            performDFSTraversal(containingMethodSignature, maxDepth, currentDepth + 1, visitedMethods);
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private void printAffectedMethods() {
//        for (String method : affectedMethods) {
//            System.out.println(method);
//        }
//    }
//
//    private String getOldFileContent(VirtualFile file) throws IOException {
//        String projectBasePath = project.getBasePath();
//        File repoDir = new File(projectBasePath);
//        String absoluteFilePath = file.getPath();
//        String relativeFilePath = absoluteFilePath.substring(projectBasePath.length() + 1); // Convert to relative path
//
//        try (Git git = Git.open(repoDir)) {
//            Repository repository = git.getRepository();
//            ObjectId headId = repository.resolve("HEAD");
//            if (headId == null) {
//                throw new IOException("Couldn't resolve HEAD");
//            }
//
//            try (RevWalk revWalk = new RevWalk(repository)) {
//                RevCommit headCommit = revWalk.parseCommit(headId);
//                revWalk.markStart(headCommit);
//
//                RevCommit previousCommit = null;
//                int count = 0;
//                for (RevCommit commit : revWalk) {
//                    count++;
//                    if (count == 2) {
//                        previousCommit = commit;
//                        break;
//                    }
//                }
//
//                if (previousCommit == null) {
//                    throw new IOException("No previous commit found");
//                }
//
//                ObjectId treeId = previousCommit.getTree().getId();
//                try (TreeWalk treeWalk = new TreeWalk(repository)) {
//                    treeWalk.addTree(treeId);
//                    treeWalk.setRecursive(true);
//                    treeWalk.setFilter(PathFilter.create(relativeFilePath));
//
//                    while (treeWalk.next()) {
//                        if (treeWalk.getPathString().equals(relativeFilePath)) {
//                            ObjectId objectId = treeWalk.getObjectId(0);
//                            try (ObjectReader objectReader = repository.newObjectReader()) {
//                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                                objectReader.open(objectId).copyTo(outputStream);
//                                return outputStream.toString(StandardCharsets.UTF_8.name());
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    private String getNewFileContent(VirtualFile file) {
//        try {
//            byte[] content = file.contentsToByteArray();
//            return new String(content);
//        } catch (IOException e) {
//            throw new RuntimeException("Error reading new file content", e);
//        }
//    }
//
//    private void compareMethods(CompilationUnit oldCompilationUnit, CompilationUnit newCompilationUnit) {
//        List<MethodDeclaration> oldMethods = new ArrayList<>();
//        List<MethodDeclaration> newMethods = new ArrayList<>();
//        oldCompilationUnit.accept(new MethodVisitor(), oldMethods);
//        newCompilationUnit.accept(new MethodVisitor(), newMethods);
//
//        Map<String, MethodDeclaration> oldMethodsMap = new HashMap<>();
//        for (MethodDeclaration method : oldMethods) {
//            oldMethodsMap.put(method.getName().asString(), method);
//        }
//
//        for (MethodDeclaration newMethod : newMethods) {
//            if (!oldMethodsMap.containsKey(newMethod.getName().asString())) {
//                System.out.println("Method added: " + newMethod.getName());
//                changes.add(newMethod.getSignature().asString());
//            } else {
//                MethodDeclaration oldMethod = oldMethodsMap.get(newMethod.getName().asString());
//                if (!oldMethod.getBody().equals(newMethod.getBody())) {
//                    System.out.println("Method changed: " + newMethod.getName().asString());
//                    changes.add(newMethod.getSignature().asString());
//                }
//                oldMethodsMap.remove(newMethod.getName().asString());
//            }
//        }
//
//        for (String removedMethodName : oldMethodsMap.keySet()) {
//            System.out.println("Method removed: " + removedMethodName);
//            changes.add(oldMethodsMap.get(removedMethodName).getSignature().asString());
//        }
//    }
//
//    private String getMethodSignature(PsiMethod method) {
//        StringBuilder signatureBuilder = new StringBuilder();
//        signatureBuilder.append(method.getName()).append('(');
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        for (int i = 0; i < parameters.length; i++) {
//            signatureBuilder.append(parameters[i].getType().getPresentableText());
//            if (i < parameters.length - 1) {
//                signatureBuilder.append(", ");
//            }
//        }
//        signatureBuilder.append(')');
//        return signatureBuilder.toString();
//    }
//
//    private String extractMethodName(String methodSignature) {
//        int startIndex = methodSignature.indexOf('(');
//        if (startIndex != -1) {
//            return methodSignature.substring(0, startIndex);
//        }
//        return methodSignature;
//    }
//
//    private String[] extractParameterTypes(String methodSignature) {
//        int startIndex = methodSignature.indexOf('(');
//        int endIndex = methodSignature.indexOf(')');
//        if (startIndex != -1 && endIndex != -1) {
//            String params = methodSignature.substring(startIndex + 1, endIndex);
//            return params.isEmpty() ? new String[0] : params.split(",\\s*");
//        }
//        return new String[0];
//    }
//
//    private boolean isMatchingParameters(PsiMethod method, String[] parameterTypes) {
//        PsiParameter[] parameters = method.getParameterList().getParameters();
//        if (parameters.length != parameterTypes.length) {
//            return false;
//        }
//        for (int i = 0; i < parameters.length; i++) {
//            if (!parameters[i].getType().getPresentableText().equals(parameterTypes[i])) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    private static class MethodVisitor extends VoidVisitorAdapter<List<MethodDeclaration>> {
//        @Override
//        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<MethodDeclaration> collector) {
//            super.visit(classOrInterfaceDeclaration, collector);
//            classOrInterfaceDeclaration.getMembers().forEach(member -> {
//                if (member instanceof MethodDeclaration) {
//                    MethodDeclaration method = (MethodDeclaration) member;
//                    collector.add(method);
//                }
//            });
//        }
//    }
//}
