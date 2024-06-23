package com.your.projectroot;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.eclipse.jgit.api.Git;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class RunChangeTrackingAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            CustomDialog dialog = new CustomDialog();
            if (dialog.showAndGet()) {
                String input = dialog.getDepth();
                boolean checkPrevious = dialog.isCheckPreviousCommit();
                if (input != null && !input.isEmpty()) {
                    try {
                        int depth = Integer.parseInt(input);

                        final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
                        changeTrackingService.trackChangesAndRunTests(depth);

                        CountDownLatch latch = new CountDownLatch(1);

                        Task.Backgroundable task1 = new Task.Backgroundable(project, "Running change tracking") {
                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {

                                checkPreviousCommitTests(project, checkPrevious,latch);

                                try {
                                    // Wait for the latch to be released before continuing
                                    latch.await();
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        showErrorDialog(project, "Await interrupted", "Error");
                                    });
                                }

                                if(checkPrevious){
                                    System.out.println("Popping Called");
                                    ApplicationManager.getApplication().invokeLater(()-> {
                                        try (Git git = Git.open(new File(Objects.requireNonNull(project.getBasePath())))) {
                                            git.stashApply().setStashRef("stash@{0}").call();
                                        } catch (Exception ex) {
                                            showErrorDialog(project,"Error in Popping","Popping Error");
                                        }
                                    });

                                    System.out.println("Popping Ended");
                                }

                                trackChangesAndNotify(project,e,latch);

                            }
                        };
                        ProgressManager.getInstance().run(task1);
                    } catch (NumberFormatException ex) {
                        showErrorDialog(project, "Please enter a valid number for depth level.", "Invalid Input");
                    }
                } else {
                    showErrorDialog(project, "Depth level input is required.", "Input Required");
                }
            }
        } else {
            System.out.println("Inside actionPerformed, project is null");
        }
    }

    private void trackChangesAndNotify(Project project, @NotNull AnActionEvent e, CountDownLatch latch) {
        final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
        ApplicationManager.getApplication().invokeLater(()->{
            changeTrackingService.runTestsOnCurrentState(latch);
        });

        //CustomActionUtil.performActionDumbAwareWithCallbacks(e.getActionManager().getAction("com.your.projectroot.RunChangeTrackingAction"),e);
        //CustomAction.doPerformActionOrShowPopup(project,e);

        ApplicationManager.getApplication().invokeLater(() -> {
            displayNotification(project);
        });
    }

    private void checkPreviousCommitTests(Project project,boolean checkPrevious,CountDownLatch latch) {
        if(checkPrevious){
            final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);

            changeTrackingService.runTestsOnHeadFiles(latch);
            //CustomAction.doPerformActionOrShowPopup(project,e);

            displayNotification(project);
        }
        else{
            latch.countDown();
        }
    }

    private void performCustomLogic(Project project) {
        // Perform custom logic by calling the new CustomActionUtil class

    }

    private void displayNotification(Project project) {
        final NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CustomNotifications");
        if (notificationGroup != null) {
            final Notification notification = notificationGroup.createNotification(
                    "Change tracking",
                    "Tracking changes and finding method usages",
                    NotificationType.INFORMATION
            );
            Notifications.Bus.notify(notification, project);
        } else {
            System.err.println("Notification group 'CustomNotifications' not found");
        }
    }

    private void showErrorDialog(Project project, String message, String title) {
        Messages.showErrorDialog(project, message, title);
    }
}
