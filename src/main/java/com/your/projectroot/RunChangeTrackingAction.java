package com.your.projectroot;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class RunChangeTrackingAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("Inside actionPerformed");
        Project project = e.getProject();
        if (project != null) {
            System.out.println("Inside actionPerformed, project is not null");
            ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
            System.out.println("Inside actionPerformed, changeTrackingService = " + changeTrackingService);
            changeTrackingService.trackChangesAndRunTests();

            NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CustomNotifications");
            if (notificationGroup != null) {
                Notification notification = notificationGroup.createNotification(
                        "Change Tracking",
                        "Tracking changes and running affected tests",
                        NotificationType.INFORMATION
                );
                Notifications.Bus.notify(notification, project);
            } else {
                System.err.println("Notification group 'CustomNotifications' not found");
            }
        } else {
            System.out.println("Inside actionPerformed, project is null");
        }
//        Project project = e.getProject();
//        if (project == null) {
//            Messages.showErrorDialog("No project found", "Error");
//            return;
//        }
//
//        String filePath = Messages.showInputDialog(project, "Enter the file URL:", "Input File URL", Messages.getQuestionIcon());
//        if (filePath == null || filePath.trim().isEmpty()) {
//            Messages.showErrorDialog("File URL cannot be empty", "Error");
//            return;
//        }
//
//        ApplicationManager.getApplication().runReadAction(() -> {
//            VirtualFile file = VcsUtil.getVirtualFile(filePath);
//            System.out.println(file);
//            if (file == null) {
//                Messages.showErrorDialog("File not found: " + filePath, "Error");
//                return;
//            }
//
//            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
//            try {
//                Change[] changes = changeListManager.getChangesIn(file).toArray(new Change[1]);
//                if (changes.length == 0) {
//                    System.out.println("No revisions found for file: " + filePath);
//                    return;
//                }
//
//                System.out.println("Revisions for file: " + filePath);
//                for (Change change : changes) {
//                    byte[] contentBytes = change.getBeforeRevision().getContent().getBytes();
//                    if (contentBytes != null) {
//                        String content = new String(contentBytes, StandardCharsets.UTF_8);
//                        System.out.println("Revision: " + change.getBeforeRevision().getRevisionNumber() + " - Content: " + content);
//                    }
//                }
//            } catch (VcsException ex) {
//                ex.printStackTrace();
//            }
//        });
    }
}

