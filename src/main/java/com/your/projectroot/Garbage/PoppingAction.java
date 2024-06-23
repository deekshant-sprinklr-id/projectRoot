//package com.your.projectroot;
//
//import com.intellij.notification.*;
//import com.intellij.openapi.actionSystem.AnAction;
//import com.intellij.openapi.actionSystem.AnActionEvent;
//import com.intellij.openapi.actionSystem.ex.ActionUtil;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.ui.Messages;
//import org.eclipse.jgit.api.Git;
//import org.jetbrains.annotations.NotNull;
//
//import java.io.File;
//import java.util.Objects;
//
//public class PoppingAction extends AnAction {
//
//    @Override
//    public void actionPerformed(@NotNull AnActionEvent e) {
//        ActionUtil.performActionDumbAwareWithCallbacks(e.getActionManager().getAction("com.your.projectroot.RunChangeTrackingAction"),e);
//        System.out.println(System.currentTimeMillis());
//        Project project = e.getProject();
//        if (project != null) {
//            popStashedChanges(project);
//        }
//        System.out.println(System.currentTimeMillis());
//    }
//
//    public void popStashedChanges(Project project) {
//        System.out.println("Popping Called");
//        ApplicationManager.getApplication().invokeLater(()-> {
//            try (Git git = Git.open(new File(Objects.requireNonNull(project.getBasePath())))) {
//                git.stashApply().setStashRef("stash@{0}").call();
//                displayNotification(project);
//            } catch (Exception ex) {
//                showErrorDialog(project);
//            }
//        });
//
//        System.out.println("Popping Ended");
//    }
//
//    private void displayNotification(Project project) {
//        final NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CustomNotifications");
//        if (notificationGroup != null) {
//            final Notification notification = notificationGroup.createNotification(
//                    "Popping action",
//                    "Unstashing the changes",
//                    NotificationType.INFORMATION
//            );
//            Notifications.Bus.notify(notification, project);
//        } else {
//            System.err.println("Notification group 'CustomNotifications' not found");
//        }
//    }
//
//    private void showErrorDialog(Project project) {
//        Messages.showErrorDialog(project, "Cannot pop", "Error");
//    }
//}
