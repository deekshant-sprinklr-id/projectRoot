//package com.your.projectroot;
//
//import com.intellij.execution.BeforeRunTask;
//import com.intellij.execution.BeforeRunTaskProvider;
//import com.intellij.execution.configurations.RunConfiguration;
//import com.intellij.execution.runners.ExecutionEnvironment;
//import com.intellij.openapi.actionSystem.DataContext;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.util.Key;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.api.errors.GitAPIException;
//import org.eclipse.jgit.api.errors.StashApplyFailureException;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import javax.swing.*;
//import java.io.File;
//import java.io.IOException;
//import java.util.Objects;
//
//public class PopChangesBeforeRunTaskProvider extends BeforeRunTaskProvider<PopChangesBeforeRunTaskProvider.PopChangesBeforeRunTask> {
//
//    private static final Logger logger = Logger.getInstance(PopChangesBeforeRunTaskProvider.class);
//    private static final Key<PopChangesBeforeRunTask> POP_TASK_KEY = Key.create("PopChangesBeforeRunTask");
//
//    @NotNull
//    @Override
//    public Key<PopChangesBeforeRunTask> getId() {
//        return POP_TASK_KEY;
//    }
//
//    @Nullable
//    @Override
//    public Icon getIcon() {
//        return null; // Provide an appropriate icon if needed
//    }
//
//    @Nullable
//    @Override
//    public String getName() {
//        return "Pop Changes";
//    }
//
//    @Nullable
//    @Override
//    public String getDescription(PopChangesBeforeRunTask task) {
//        return "Pops local changes before running the configuration.";
//    }
//
//    @Override
//    public boolean isConfigurable() {
//        return false;
//    }
//
//    @Override
//    public @Nullable PopChangesBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
//        return new PopChangesBeforeRunTask();
//    }
//
//    @Override
//    public boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment environment, @NotNull PopChangesBeforeRunTask task) {
//        Project project = environment.getProject();
//        try (Git git = Git.open(new File(Objects.requireNonNull(project.getBasePath())))) {
//            System.out.println("Executing PopChangesBeforeRunTask for configuration: " + configuration.getName());
//            //Stashing
//            System.out.println("Stashing Stared\n");
//            git.stashCreate().call();
//            System.out.println("Stashing ended\n");
//            System.out.println("\nRUNNING\n");
//            final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
//            changeTrackingService.runTestsOnHeadCommitFiles();
//            return true;
//        } catch (StashApplyFailureException e) {
//            logger.error("Conflict during popping changes", e);
//            return false;
//        } catch (GitAPIException | IOException e) {
//            logger.error("Error during popping changes", e);
//            return false;
//        }
//    }
//
//    public static class PopChangesBeforeRunTask extends BeforeRunTask<PopChangesBeforeRunTaskProvider.PopChangesBeforeRunTask> {
//        public PopChangesBeforeRunTask() {
//            super(POP_TASK_KEY);
//        }
//    }
//}
//
//
//
