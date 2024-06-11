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
            String input = Messages.showInputDialog(
                    project,
                    "Enter the depth level for the method usage search:",
                    "Input Depth Level",
                    Messages.getQuestionIcon()
            );
            if (input != null && !input.isEmpty()) {
                try {
                    int depth = Integer.parseInt(input);
                    final ChangeTrackingService changeTrackingService = project.getService(ChangeTrackingService.class);
                    changeTrackingService.trackChangesAndRunTests(depth);

                    // Implementing the Notification Feature
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
                } catch (NumberFormatException ex) {
                    Messages.showErrorDialog(project, "Please enter a valid number for depth level.", "Invalid Input");
                }
            } else {
                Messages.showErrorDialog(project, "Depth level input is required.", "Input Required");
            }
        } else {
            System.out.println("Inside actionPerformed, project is null");
        }
    }
}
