package com.your.projectroot;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents an action that tracks code changes and runs tests based on user-specified depth levels.
 * When the action is performed, it prompts the user to input the depth level for method usage search,
 * then initiates the change tracking process and displays a notification.
 */
public class RunChangeTrackingAction extends AnAction {

    /**
     * This method is responsible for performing the action of the plugin and displaying a notification when the user
     * calls the plugin. It prompts the user to enter the depth level for method usage search and calls the service
     * that handles the logic of the plugin.
     *
     * @param e The event that triggers the plugin action.
     */
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
                        synchronized (this){
                            trackChangesAndNotify(project, depth);
                        }
                        if (checkPrevious) {
                            checkPreviousCommitTests(project);
                        }
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

    /**
     * Tracks changes and runs tests, then displays a notification.
     *
     * @param project The IntelliJ project.
     * @param depth   The depth level for method usage search.
     */
    private void trackChangesAndNotify(Project project, int depth) {
        final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
        changeTrackingService.trackChangesAndRunTests(depth);
        displayNotification(project);
    }

    private void checkPreviousCommitTests(Project project) {
        final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
        changeTrackingService.runTestsOnHeadFiles();
        displayNotification(project);
    }

    /**
     * Displays a notification.
     *
     * @param project The IntelliJ project.
     */
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

    /**
     * Shows an error dialog with the specified message and title.
     *
     * @param project The IntelliJ project.
     * @param message The error message.
     * @param title   The title of the error dialog.
     */
    private void showErrorDialog(Project project, String message, String title) {
        Messages.showErrorDialog(project, message, title);
    }
}
