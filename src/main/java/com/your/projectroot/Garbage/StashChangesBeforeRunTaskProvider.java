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
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import javax.swing.*;
//import java.io.File;
//import java.util.Objects;
//
//public class StashChangesBeforeRunTaskProvider extends BeforeRunTaskProvider<StashChangesBeforeRunTaskProvider.StashChangesBeforeRunTask> {
//    private static final Logger logger = Logger.getInstance(StashChangesBeforeRunTaskProvider.class);
//    private static final Key<StashChangesBeforeRunTask> STASH_TASK_KEY = Key.create("StashChangesBeforeRunTask");
//
//    @NotNull
//    @Override
//    public Key<StashChangesBeforeRunTask> getId() {
//        return STASH_TASK_KEY;
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
//        return "Stash Changes";
//    }
//
//    @Nullable
//    @Override
//    public String getDescription(StashChangesBeforeRunTask task) {
//        return "Stashes local changes before running the configuration.";
//    }
//
//    @Override
//    public boolean isConfigurable() {
//        return false;
//    }
//
//    @Override
//    public @Nullable StashChangesBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
//        return new StashChangesBeforeRunTask();
//    }
//
//    @Override
//    public boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment environment, @NotNull StashChangesBeforeRunTask task) {
//        Project project = environment.getProject();
//        try (Git git = Git.open(new File(Objects.requireNonNull(project.getBasePath())))) {
//            System.out.println("Executing StashChangesBeforeRunTask for configuration: " + configuration.getName());
//            git.stashCreate().call();
//            return true;
//        } catch (Exception e) {
//            logger.error("Error during stashing changes", e);
//            return false;
//        }
//    }
//
//    public static class StashChangesBeforeRunTask extends BeforeRunTask<StashChangesBeforeRunTask> {
//        public StashChangesBeforeRunTask() {
//            super(STASH_TASK_KEY);
//        }
//    }
//}
