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
import com.intellij.psi.PsiModifier;
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
    public static final List<String> CHANGES = new ArrayList<>();
    private final Map<String, Integer> AFFECTED_METHODS = new HashMap<>();
    private final Set<PsiMethod> PRIVATE_METHODS = new HashSet<>();
    private final Set<PsiMethod> PUBLIC_METHOD_TESTS = new HashSet<>();

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
                    //might cause problem with this initializing
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

        //Printing Changes
        System.out.println(AFFECTED_METHODS);
        //Printing Private Methods
        System.out.println(PRIVATE_METHODS);
        //Printing Tests of Public Methods
        System.out.println(PUBLIC_METHOD_TESTS);

        Set<PsiMethod> privateUsages = PrivateMethodUsageFinder.findPrivateMethodUsages(project,PRIVATE_METHODS);
        System.out.println(privateUsages);
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
                //Adding Private Methods
                if(method.hasModifierProperty(PsiModifier.PRIVATE)){
                    PRIVATE_METHODS.add(method);
                }
                //Adding Public Method Test
                if(CustomUtil.isTestMethod(method)){
                    PUBLIC_METHOD_TESTS.add(method);
                }
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
                            String methodSignature = CustomUtil.getMethodSignatureForPsiElement(containingMethod, methodClass);
                            System.out.println("Method " + callingMethod + " is used in: " + methodSignature);
                            findUsagesForMethod(methodSignature, maxDepth, currentDepth + 1, currentPath);
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

    //Below Part need to be made

//    public void runAffectedTests() {
//        Set<String> affectedMethodsSignatures = AFFECTED_METHODS.keySet();
//        Set<PsiMethod> affectedMethods = getAffectedMethods(affectedMethodsSignatures);
//
////        Set<PsiMethod> publicMethodTests = CheckPublicMethodTest.findTestsForPublicMethods(affectedMethods);
////        for(PsiMethod m: publicMethodTests){
////            System.out.println(m.getName());
////        }
//        Set<PsiMethod> privateMethodTests = CheckPrivateMethods.findTestsForPrivateMethods(affectedMethods,project);
//        for(PsiMethod m: privateMethodTests){
//            System.out.println(m.getName());
//        }
//        Set<PsiMethod> allTests = new HashSet<>();
//        //allTests.addAll(publicMethodTests);
//        allTests.addAll(privateMethodTests);
//
//        for(PsiMethod m: allTests){
//            System.out.println(m.getName());
//        }
//        //runTests(allTests);
//    }
//
//    private Set<PsiMethod> getAffectedMethods(Set<String> affectedMethodsSignatures) {
//        Set<PsiMethod> affectedMethods = new HashSet<>();
//        for (String methodSignature : affectedMethodsSignatures) {
//            PsiMethod psiMethod = CustomUtil.convertSignatureToPsiMethod(methodSignature,project);
//            if (psiMethod != null) {
//                affectedMethods.add(psiMethod);
//            }
//        }
//        for(PsiMethod m: affectedMethods){
//            System.out.println(m);
//        }
//        return affectedMethods;
//    }
//
//
//    public void runTests(Set<PsiMethod> testMethods) {
//        Launcher launcher = LauncherFactory.create();
//        System.out.println("Available Test Engines: " + launcher);
//
//        SummaryGeneratingListener listener = new SummaryGeneratingListener();
//
//        for (PsiMethod method : testMethods) {
//            String className = method.getContainingClass().getQualifiedName();
//            String methodName = method.getName();
//
//            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
//                    .selectors(selectMethod(className, methodName))
//                    .build();
//
//            launcher.execute(request, listener);
//        }
//
//        listener.getSummary().printTo(new PrintWriter(System.out));
//    }


}


