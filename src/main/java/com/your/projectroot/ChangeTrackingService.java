package com.your.projectroot;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class ChangeTrackingService {

    private static final Logger logger = Logger.getInstance(ChangeTrackingService.class);
    private final Project project;

    public ChangeTrackingService(Project project) {
        this.project = project;
    }

    public void trackChangesAndRunTests() {
        System.out.println("Beginning");
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
                    int index=sourceFilePath.lastIndexOf("/");
                    String className=file.getPath().substring(index+1);
                    System.out.println("Changed: " + className);

                    FileInputStream fileInputStream;
                    try {
                        fileInputStream = new FileInputStream(sourceFilePath);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    CompilationUnit compilationUnit;
                    try {
                        compilationUnit = JavaParser.parse(fileInputStream);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }

                    // Visit the class to get its methods
                    compilationUnit.accept(new ClassVisitor(), null);
                } else {
                    System.out.println("File is null");
                }
            }
        }
    }
    private static class ClassVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Void arg) {
            super.visit(classOrInterfaceDeclaration, arg);

            // Print class name
            System.out.println("Class Name: " + classOrInterfaceDeclaration.getName());

            // Retrieve all methods in the class
//            classOrInterfaceDeclaration.getMembers().stream()
//                    .filter(member -> member instanceof MethodDeclaration)
//                    .map(member -> (MethodDeclaration) member)
//                    .forEach(method -> {
//                        System.out.println("Method Name: " + method.getName());
//                    });
            classOrInterfaceDeclaration.getMembers().forEach(member -> {
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) member;
                    System.out.println("Method Name: " + method.getName());
                } else if (member instanceof ConstructorDeclaration) {
                    ConstructorDeclaration constructor = (ConstructorDeclaration) member;
                    System.out.println("Constructor Name: " + constructor.getName());
                } else {
                    System.out.println("Other Member: " + member);
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
