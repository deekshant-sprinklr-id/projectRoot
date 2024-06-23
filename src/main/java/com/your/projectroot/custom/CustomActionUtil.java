package com.your.projectroot.custom;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class CustomActionUtil {
    private static final Logger LOG = Logger.getInstance(ActionUtil.class);
    @ApiStatus.Internal
    public static final Key<Boolean> ALLOW_ACTION_PERFORM_WHEN_HIDDEN = Key.create("ALLOW_ACTION_PERFORM_WHEN_HIDDEN");

    public static void performActionDumbAwareWithCallbacks(@NotNull AnAction action, @NotNull AnActionEvent e) {
        performDumbAwareWithCallbacks(action, e, () -> doPerformActionOrShowPopup(action, e, null));
    }
    public static void performDumbAwareWithCallbacks(@NotNull AnAction action,
                                                     @NotNull AnActionEvent event,
                                                     @NotNull Runnable performRunnable) {
        Project project = event.getProject();
        IndexNotReadyException indexError = null;
        ActionManagerEx manager = ActionManagerEx.getInstanceEx();
        manager.fireBeforeActionPerformed(action, event);
        Component component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
        if (component != null && !UIUtil.isShowing(component) &&
                !ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()) &&
                !Boolean.TRUE.equals(ClientProperty.get(component, ALLOW_ACTION_PERFORM_WHEN_HIDDEN))) {
            String id = StringUtil.notNullize(event.getActionManager().getId(action), action.getClass().getName());
            LOG.warn("Action is not performed because target component is not showing: " +
                    "action=" + id + ", component=" + component.getClass().getName());
            manager.fireAfterActionPerformed(action, event, AnActionResult.IGNORED);
            return;
        }
        AnActionResult result = null;
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
            performRunnable.run();
            result = AnActionResult.PERFORMED;
        }
        catch (IndexNotReadyException ex) {
            indexError = ex;
            result = AnActionResult.failed(ex);
        }
        catch (RuntimeException | Error ex) {
            result = AnActionResult.failed(ex);
            throw ex;
        }
        finally {
            if (result == null) result = AnActionResult.failed(new Throwable());
            manager.fireAfterActionPerformed(action, event, result);
        }
        if (indexError != null) {
            LOG.info(indexError);
            showDumbModeWarning(project, event);
        }
    }


    @ApiStatus.Internal
    public static void doPerformActionOrShowPopup(@NotNull AnAction action,
                                                  @NotNull AnActionEvent e,
                                                  @Nullable Consumer<? super JBPopup> popupShow) {
        if (action instanceof ActionGroup group && !e.getPresentation().isPerformGroup()) {
            DataContext dataContext = e.getDataContext();
            String place = ActionPlaces.getActionGroupPopupPlace(e.getPlace());
            ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                    e.getPresentation().getText(), group, dataContext,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false, null, -1, null, place);
            var toolbarPopupLocation = CommonActionsPanel.getPreferredPopupPoint(action, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
            if (toolbarPopupLocation != null) {
                popup.show(toolbarPopupLocation);
            }
            else if (popupShow != null) {
                popupShow.accept(popup);
            }
            else {
                popup.showInBestPositionFor(dataContext);
            }
        }
        else {
            action.actionPerformed(e);
        }
    }

    public static void showDumbModeWarning(@Nullable Project project, AnActionEvent @NotNull ... events) {
        List<String> actionNames = new ArrayList<>();
        for (AnActionEvent event : events) {
            String s = event.getPresentation().getText();
            if (StringUtil.isNotEmpty(s)) {
                actionNames.add(s);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Showing dumb mode warning for " + Arrays.asList(events), new Throwable());
        }
        if (project == null) return;
        DumbService.getInstance(project).showDumbModeNotification(getActionUnavailableMessage(actionNames));
    }

    private static @NotNull @NlsContexts.PopupContent String getActionUnavailableMessage(@NotNull List<String> actionNames) {
        String message;
        if (actionNames.isEmpty()) {
            message = getUnavailableMessage("This action", false);
        }
        else if (actionNames.size() == 1) {
            message = getUnavailableMessage("'" + actionNames.get(0) + "'", false);
        }
        else {
            message = getUnavailableMessage("None of the following actions", true) +
                    ": " + StringUtil.join(actionNames, ", ");
        }
        return message;
    }

    public static @NotNull @NlsContexts.PopupContent String getUnavailableMessage(@NotNull String action, boolean plural) {
        if (plural) {
            return IdeBundle.message("popup.content.actions.not.available.while.updating.indices", action,
                    ApplicationNamesInfo.getInstance().getProductName());
        }
        return IdeBundle.message("popup.content.action.not.available.while.updating.indices", action,
                ApplicationNamesInfo.getInstance().getProductName());
    }
}
