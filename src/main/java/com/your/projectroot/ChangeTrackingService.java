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
import com.intellij.psi.*;
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
    public static final List<String> CHANGES = new ArrayList<>();
    private final Map<String, Integer> AFFECTED_METHODS = new HashMap<>();

    public ChangeTrackingService(Project project) {
        this.project = project;
    }

    public void trackChangesAndRunTests(int maxDepth) {
        System.out.println("Beginning: " + maxDepth);
        final ChangeListManager changeListManager = ChangeListManager.getInstance(project);

        // Get the list of local changes
        final List<LocalChangeList> changes = changeListManager.getChangeLists();

        // Iterate over changes and process them

        for (LocalChangeList changeList : changes) {
            for (Change change : changeList.getChanges()) {
                VirtualFile file = change.getVirtualFile();

                if (file != null) {
                    // Process each changed file to determine affected unit tests
                    final String sourceFilePath = file.getPath();
                    System.out.println("Changed Class: " + sourceFilePath);
                    int lastInd = sourceFilePath.lastIndexOf('.');
                    int startInd = sourceFilePath.lastIndexOf('/');
                    String ClassName = sourceFilePath.substring(startInd + 1, lastInd);
                    // Get old and new content of the file
                    String oldContent = null;
                    try {
                        oldContent = getOldFileContent(file);

                    } catch (IOException e) {
                        System.out.println("Cannot get OLD file content");
                        logger.info("Cannot get OLD file content");
                    }
                    //todo:might cause problem with this initializing
                    String newContent = "";
                    try {
                        newContent = getNewFileContent(file);
                    } catch (RuntimeException ex) {
                        System.out.println("Cannot get NEW file content");
                        logger.info("Cannot get NEW file content");
                    }

                    if (oldContent != null) {
                        final JavaParser parser = new JavaParser();
                        final CompilationUnit oldCompilationUnit = parser.parse(new ByteArrayInputStream(oldContent.getBytes())).getResult().orElse(null);
                        final CompilationUnit newCompilationUnit = parser.parse(new ByteArrayInputStream(newContent.getBytes())).getResult().orElse(null);

                        // Compare methods
                        compareMethods(oldCompilationUnit, newCompilationUnit, ClassName);
                    } else {
                        logger.info("Past Commit Content is null");
                    }

                } else {
                    System.out.println("File is null");
                }
            }
        }

        findMethodUsages(maxDepth);

        //Checking Changes
        System.out.println(AFFECTED_METHODS);
    }

    private String getOldFileContent(VirtualFile file) throws IOException {
        String projectBasePath = project.getBasePath();
        String absoluteFilePath = file.getPath();
        File repoDir = null;
        String relativeFilePath = null;
        if (projectBasePath != null) {
            repoDir = new File(projectBasePath);
            //Convert to relative path
            relativeFilePath = absoluteFilePath.substring(projectBasePath.length() + 1);
        } else {
            logger.info("Project's base path is null");
        }


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

                    if (relativeFilePath != null) {
                        treeWalk.setFilter(PathFilter.create(relativeFilePath));
                    } else {
                        logger.info("Relative Path is null");
                    }

                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        if (path.equals(relativeFilePath)) {
                            // Get the file content
                            ObjectId objectId = treeWalk.getObjectId(0);
                            try (ObjectReader objectReader = repository.newObjectReader()) {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                objectReader.open(objectId).copyTo(outputStream);
                                return outputStream.toString(StandardCharsets.UTF_8);
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
            logger.info("Error reading new file content");
            throw new RuntimeException("Error reading new file content", e);
        }
    }

    private void compareMethods(CompilationUnit oldCompilationUnit, CompilationUnit newCompilationUnit, String ClassName) {
        if (oldCompilationUnit != null && newCompilationUnit != null) {
            final List<MethodDeclaration> oldMethods = new ArrayList<>();
            final List<MethodDeclaration> newMethods = new ArrayList<>();
            oldCompilationUnit.accept(new MethodVisitor(), oldMethods);
            newCompilationUnit.accept(new MethodVisitor(), newMethods);
            Map<String, MethodDeclaration> oldMethodsMap = new HashMap<>();
            for (MethodDeclaration method : oldMethods) {
                oldMethodsMap.put(CustomUtil.getSignOfMethodDeclaration(method.getSignature(), ClassName), method);
            }

            for (MethodDeclaration newMethod : newMethods) {
                if (!oldMethodsMap.containsKey(CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName))) {
                    System.out.println("Method added: " + CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                    CHANGES.add(CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                } else {
                    MethodDeclaration oldMethod = oldMethodsMap.get(CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                    if (!oldMethod.getBody().equals(newMethod.getBody())) {
                        System.out.println("Method changed: " + CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                        CHANGES.add(CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                    }
                    oldMethodsMap.remove(CustomUtil.getSignOfMethodDeclaration(newMethod.getSignature(), ClassName));
                }
            }


            for (String removedMethodName : oldMethodsMap.keySet()) {
                System.out.println("Method removed: " + removedMethodName);
                CHANGES.add(removedMethodName);
            }
        } else {
            if (oldCompilationUnit == null) {
                logger.info("Getting Old Compilation as null");
            } else {
                logger.info("Getting New Compilation as null");
            }
        }
    }

    private void findMethodUsages(int maxDepth) {
        for (String c : CHANGES) {
            System.out.println(c);
        }

        for (String change : CHANGES) {
            System.out.println(change + " is Called");
            findUsagesForMethod(change, maxDepth, 0, new HashSet<>());
        }

    }

    private void findUsagesForMethod(String callingMethod, int maxDepth, int currentDepth, Set<String> currentPath) {
        if (currentDepth > maxDepth) {
            return;
        }
        if (currentPath.contains(callingMethod)) {
            System.out.println("Cycle detected at: " + callingMethod);
            return;
        }

        currentPath.add(callingMethod);

        if (AFFECTED_METHODS.containsKey(callingMethod) && AFFECTED_METHODS.get(callingMethod) <= currentDepth) {
            return;
        }

        AFFECTED_METHODS.put(callingMethod, currentDepth);

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        String methodName = CustomUtil.extractMethodName(callingMethod);
        String className = CustomUtil.extractClassName(callingMethod);
        PsiMethod[] psiMethods = shortNamesCache.getMethodsByName(methodName, scope);
        String[] parameterTypes = CustomUtil.extractParameterTypes(callingMethod);

        for (PsiMethod method : psiMethods) {
            if (CustomUtil.isMatchingParameters(method, parameterTypes)) {
                Collection<PsiReference> references = ReferencesSearch.search(method, scope).findAll();
                for (PsiReference reference : references) {
                    PsiElement element = reference.getElement();
                    String containingClass = CustomUtil.findDeclaringClassName(element);
                    if (containingClass != null && Objects.equals(containingClass, className)) {
                        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                        String methodClass = null;
                        if (containingMethod != null) {
                            methodClass = Objects.requireNonNull(containingMethod.getContainingClass()).getQualifiedName();
                        }
                        if (containingMethod != null) {
                            System.out.println("Method " + callingMethod + " is used in: " + CustomUtil.getMethodSignatureForPsiElement(containingMethod,methodClass));
                            findUsagesForMethod(CustomUtil.getMethodSignatureForPsiElement(containingMethod,methodClass), maxDepth, currentDepth + 1, currentPath);
                        }
                    }
                }
            }
        }

        //Removing Method from current path after traversal
        currentPath.remove(callingMethod);
    }


    private static class MethodVisitor extends VoidVisitorAdapter<List<MethodDeclaration>> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<MethodDeclaration> collector) {
            super.visit(classOrInterfaceDeclaration, collector);
            classOrInterfaceDeclaration.getMembers().forEach(member -> {
                if (member instanceof MethodDeclaration method) {
                    collector.add(method);
                }
            });
        }
    }

}


