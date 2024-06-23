package com.your.projectroot.Garbage;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.function.Consumer;
import java.util.function.Function;

public class Execution {

    private static final Logger LOG = Logger.getInstance(Execution.class);
    public static final String PROPERTY_DYNAMIC_CLASSPATH = "dynamic.classpath";
    private static final NotificationGroup ourSilentNotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("Silent Execution");
    private static final NotificationGroup ourNotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("Execution");

    public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor) {
        doRunConfiguration(configuration, executor, null, null, null);
    }


    public static void doRunConfiguration(@NotNull RunnerAndConfigurationSettings configuration,
                                          @NotNull Executor executor,
                                          @Nullable ExecutionTarget targetOrNullForDefault,
                                          @Nullable Long executionId,
                                          @Nullable DataContext dataContext) {
        doRunConfiguration(configuration, executor, targetOrNullForDefault, executionId, dataContext, null);
    }

    public static void doRunConfiguration(@NotNull RunnerAndConfigurationSettings configuration,
                                          @NotNull Executor executor,
                                          @Nullable ExecutionTarget targetOrNullForDefault,
                                          @Nullable Long executionId,
                                          @Nullable DataContext dataContext,
                                          @Nullable Consumer<? super ExecutionEnvironment> environmentCustomization) {
        ExecutionEnvironmentBuilder builder = createEnvironment(executor, configuration);
        if (builder == null) {
            return;
        }

        if (targetOrNullForDefault != null) {
            builder.target(targetOrNullForDefault);
        }
        else {
            builder.activeTarget();
        }
        if (executionId != null) {
            builder.executionId(executionId);
        }
        if (dataContext != null) {
            builder.dataContext(IdeUiService.getInstance().createAsyncDataContext(dataContext));
        }

        ExecutionEnvironment environment = ApplicationManager.getApplication().isDispatchThread() ?
                ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> builder.build()), ExecutionBundle.message("dialog.title.preparing.execution"), true, null) :
                builder.build();
        if(environmentCustomization != null) {
            environmentCustomization.accept(environment);
        }

        ExecutionManager.getInstance(configuration.getConfiguration().getProject()).restartRunProfile(environment);
    }

    public static ExecutionEnvironmentBuilder createEnvironment(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) {
        try {
            return ExecutionEnvironmentBuilder.create(executor, settings);
        }
        catch (ExecutionException e) {
            RunConfiguration configuration = settings.getConfiguration();
            Project project = configuration.getProject();
            RunContentManager manager = RunContentManager.getInstance(project);
            String toolWindowId = manager.getContentDescriptorToolWindowId(configuration);
            if (toolWindowId == null) {
                toolWindowId = executor.getToolWindowId();
            }
            handleExecutionError(project, toolWindowId, configuration.getName(), e);
            return null;
        }
    }

    public static void handleExecutionError(@NotNull Project project,
                                            @NotNull String toolWindowId,
                                            @NotNull String taskName,
                                            @NotNull Throwable e) {
        if (e instanceof RunCanceledByUserException) {
            return;
        }

        LOG.debug(e);
        if (e instanceof CantRunException.CustomProcessedCantRunException) {
            return;
        }

        String description = e.getMessage();
        HyperlinkListener listener = null;
        if (isProcessNotCreated(e)) {
            String exePath = ((ProcessNotCreatedException)e).getCommandLine().getExePath();
            if ((SystemInfoRt.isWindows ? exePath.endsWith("java.exe") : exePath.endsWith("java")) &&
                    !PropertiesComponent.getInstance(project).isTrueValue(PROPERTY_DYNAMIC_CLASSPATH)) {
                LOG.warn("Java configuration should implement `ConfigurationWithCommandLineShortener` and provide UI to configure shortening method", e);
                description = ExecutionBundle.message("dialog.message.command.line.too.long.notification");
                listener = event -> PropertiesComponent.getInstance(project).setValue(PROPERTY_DYNAMIC_CLASSPATH, "true");
            }
        }

        handleExecutionError(project, toolWindowId, taskName, e, description, listener);
    }

    public static boolean isProcessNotCreated(@NotNull Throwable e) {
        if (e instanceof ProcessNotCreatedException) {
            String description = e.getMessage();
            return (description.contains("87") || description.contains("111") || description.contains("206") || description.contains("error=7,")) &&
                    ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString().length() > 1024 * 32;
        }
        return false;
    }

    public static void handleExecutionError(@NotNull Project project,
                                            @NotNull String toolWindowId,
                                            @NotNull String taskName,
                                            @NotNull Throwable e,
                                            @Nullable @NlsContexts.DialogMessage String description,
                                            @Nullable HyperlinkListener listener) {
        String title = ExecutionBundle.message("error.running.configuration.message", taskName);
        handleExecutionError(project, toolWindowId, e, title, description, descr -> title + ":<br>" + descr, listener);
    }

    public static void handleExecutionError(@NotNull Project project,
                                            @NotNull String toolWindowId,
                                            @NotNull Throwable e,
                                            @Nls String title,
                                            @Nullable @NlsContexts.DialogMessage String description,
                                            @NotNull Function<? super @NlsContexts.DialogMessage String, @NlsContexts.DialogMessage String> fullMessageSupplier,
                                            @Nullable HyperlinkListener listener) {

        if (StringUtil.isEmptyOrSpaces(description)) {
            LOG.warn("Execution error without description", e);
            description = ExecutionBundle.message("dialog.message.unknown.error");
        }

        String fullMessage = fullMessageSupplier.apply(description);

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            LOG.error(fullMessage, e);
        }
        else {
            LOG.info(fullMessage, e);
        }

        if (e instanceof ProcessNotCreatedException) {
            LOG.debug("Attempting to run: " + ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString());
        }

        if (listener == null) {
            listener = ExceptionUtil.findCause(e, HyperlinkListener.class);
        }

        HyperlinkListener _listener = listener;
        String _description = description;
        UIUtil.invokeLaterIfNeeded(() -> {
            if (project.isDisposed()) {
                return;
            }

            boolean balloonShown = IdeUiService.getInstance().notifyByBalloon(project, toolWindowId, MessageType.ERROR,
                    fullMessage, null, _listener);

            NotificationGroup notificationGroup = balloonShown ? ourSilentNotificationGroup : ourNotificationGroup;
            Notification notification = notificationGroup.createNotification(title, _description, NotificationType.ERROR);
            if (_listener != null) {
                notification.setListener((_notification, event) -> {
                    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        _notification.expire();
                        _listener.hyperlinkUpdate(event);
                    }
                });
            }
            notification.notify(project);
        });
    }

}
