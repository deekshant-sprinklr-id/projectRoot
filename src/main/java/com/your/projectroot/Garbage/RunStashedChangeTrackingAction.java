//package com.your.projectroot;
//
//import com.intellij.notification.*;
//import com.intellij.openapi.actionSystem.AnAction;
//import com.intellij.openapi.actionSystem.AnActionEvent;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.ui.Messages;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.api.errors.GitAPIException;
//import org.jetbrains.annotations.NotNull;
//
//import java.io.File;
//import java.util.Objects;
//
//public class RunStashedChangeTrackingAction extends AnAction {
//    @Override
//    public void actionPerformed(@NotNull AnActionEvent e) {
//        Project project = e.getProject();
//        if (project != null) {
//            runTestsOnStashedFiles(project,e);
//        } else {
//            System.out.println("Inside actionPerformed, project is null");
//        }
//    }
//
//    public void runTestsOnStashedFiles(Project project,AnActionEvent e) {
//        ApplicationManager.getApplication().invokeLater(() -> {
//            try (Git git = Git.open(new File(Objects.requireNonNull(project.getBasePath())))) {
//                String stashRef = stashChanges(git);
//
//                // Run tests on stashed files
//                runTestsOnStashedFiles(git, stashRef, project,e);
//            } catch (Exception ex) {
//                showErrorDialog(project, "Error running tests on stashed files.", "Error");
//            }
//        });
//    }
//
//    private String stashChanges(Git git) throws GitAPIException {
//        return git.stashCreate().call().getName();
//    }
//
//    private void runTestsOnStashedFiles(Git git, String stashRef, Project project, AnActionEvent e) {
//        final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
//        changeTrackingService.runTestsOnHeadCommitFiles();
//
//        ApplicationManager.getApplication().invokeLater(() -> {
//            displayNotification(project);
//            showDialog(project,e);
//        });
//    }
//
//    /**
//     * Displays a notification.
//     *
//     * @param project The IntelliJ project.
//     */
//    private void displayNotification(Project project) {
//        final NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CustomNotifications");
//        if (notificationGroup != null) {
//            final Notification notification = notificationGroup.createNotification(
//                    "Change tracking",
//                    "Tracking changes and finding method usages",
//                    NotificationType.INFORMATION
//            );
//            Notifications.Bus.notify(notification, project);
//        } else {
//            System.err.println("Notification group 'CustomNotifications' not found");
//        }
//    }
//
//    /**
//     * Shows a dialog box after the tests are run.
//     *
//     * @param project The IntelliJ project.
//     */
//    private void showDialog(Project project,AnActionEvent e) {
//        int response = Messages.showYesNoDialog(project,
//                "Tests on stashed files have been run successfully. Do you want to perform the next action?",
//                "Test Results",
//                Messages.getQuestionIcon());
//
////        if (response == Messages.YES) {
////            ActionUtil.performActionDumbAwareWithCallbacks(e.getActionManager().getAction("com.your.projectroot.PoppingAction"),e);
////        }
//    }
//
//    /**
//     * Shows an error dialog with the specified message and title.
//     *
//     * @param project The IntelliJ project.
//     * @param message The error message.
//     * @param title   The title of the error dialog.
//     */
//    private void showErrorDialog(Project project, String message, String title) {
//        Messages.showErrorDialog(project, message, title);
//    }
//}
