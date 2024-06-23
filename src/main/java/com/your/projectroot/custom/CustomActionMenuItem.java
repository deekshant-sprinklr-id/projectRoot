package com.your.projectroot.custom;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.MainMenuCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBCheckBoxMenuItem;
import com.intellij.ui.mac.screenmenu.Menu;
import com.intellij.ui.mac.screenmenu.MenuItem;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public final class CustomActionMenuItem extends JBCheckBoxMenuItem {
    static final Icon EMPTY_ICON = EmptyIcon.create(16, 1);

    private final ActionRef<AnAction> myAction;
    private final String myPlace;
    private final boolean myInsideCheckedGroup;
    private final boolean myEnableMnemonics;
    private final boolean myToggleable;
    private final DataContext myContext;
    private final boolean myUseDarkIcons;
    private final @Nullable MenuItem myScreenMenuItemPeer;

    private String myDescription;
    private boolean myToggled;
    private boolean myKeepMenuOpen;

    CustomActionMenuItem(@NotNull AnAction action,
                   @NotNull String place,
                   @NotNull DataContext context,
                   boolean enableMnemonics,
                   boolean insideCheckedGroup,
                   boolean useDarkIcons) {
        myAction = ActionRef.fromAction(action);
        myPlace = place;
        myContext = context;
        myEnableMnemonics = enableMnemonics;
        myToggleable = action instanceof Toggleable;
        myInsideCheckedGroup = insideCheckedGroup;
        myUseDarkIcons = useDarkIcons;
        addActionListener(e -> performAction(e.getModifiers()));
        setBorderPainted(false);

        if (Menu.isJbScreenMenuEnabled() && ActionPlaces.MAIN_MENU.equals(myPlace)) {
            myScreenMenuItemPeer = new MenuItem();
            myScreenMenuItemPeer.setActionDelegate(() -> {
                // Called on AppKit when user activates menu item
                if (isToggleable()) {
                    myToggled = !myToggled;
                    myScreenMenuItemPeer.setState(myToggled);
                }
                SwingUtilities.invokeLater(() -> {
                    if (myAction.getAction().isEnabledInModalContext() ||
                            !Boolean.TRUE.equals(myContext.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT))) {
                        ((CustomTransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> performAction(0));
                    }
                });
            });
        }
        else {
            myScreenMenuItemPeer = null;
        }

        updateUI();
        updateAccelerator();
    }

    public @NotNull AnAction getAnAction() {
        return myAction.getAction();
    }

    public @NotNull String getPlace() {
        return myPlace;
    }

    public @Nullable MenuItem getScreenMenuItemPeer() { return myScreenMenuItemPeer; }

    private static boolean isEnterKeyStroke(KeyStroke keyStroke) {
        return keyStroke.getKeyCode() == KeyEvent.VK_ENTER && keyStroke.getModifiers() == 0;
    }

    @Override
    public void fireActionPerformed(ActionEvent event) {
        Application app = ApplicationManager.getApplication();
        if (!app.isDisposed() && ActionPlaces.MAIN_MENU.equals(myPlace)) {
            //noinspection UnstableApiUsage
            MainMenuCollector.getInstance().record(myAction.getAction());
        }
        ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> super.fireActionPerformed(event));
    }

    private void updateAccelerator() {
        AnAction action = myAction.getAction();
        String id = ActionManager.getInstance().getId(action);
        if (id != null) {
            setAcceleratorFromShortcuts(getActiveKeymapShortcuts(id).getShortcuts());
        }
        else {
            ShortcutSet shortcutSet = action.getShortcutSet();
            setAcceleratorFromShortcuts(shortcutSet.getShortcuts());
        }
    }

    @Override
    public void setDisplayedMnemonicIndex(int index) throws IllegalArgumentException {
        super.setDisplayedMnemonicIndex(myEnableMnemonics ? index : -1);
    }

    @Override
    public void setMnemonic(int mnemonic) {
        super.setMnemonic(myEnableMnemonics ? mnemonic : 0);
    }

    private void setAcceleratorFromShortcuts(Shortcut @NotNull [] shortcuts) {
        for (Shortcut shortcut : shortcuts) {
            if (shortcut instanceof KeyboardShortcut) {
                final KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
                //If action has Enter shortcut, do not add it. Otherwise, user won't be able to chose any ActionMenuItem other than that
                if (!isEnterKeyStroke(firstKeyStroke)) {
                    setAccelerator(firstKeyStroke);
                    if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setLabel(getText(), firstKeyStroke);
                    if (KeymapUtil.isSimplifiedMacShortcuts()) {
                        final String shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts);
                        putClientProperty("accelerator.text", shortcutText);
                        if (myScreenMenuItemPeer != null) myScreenMenuItemPeer.setAcceleratorText(shortcutText);
                    }
                }
                break;
            }
        }
    }

    @Override
    public void updateUI() {
        setUI(BegMenuItemUI.createUI(this));
    }

    /**
     * Updates long description of action at the status bar.
     */
    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        super.menuSelectionChanged(isIncluded);
        //noinspection HardCodedStringLiteral
        ActionMenu.showDescriptionInStatusBar(isIncluded, this, myDescription);
    }

    @NlsSafe
    public String getFirstShortcutText() {
        return KeymapUtil.getFirstKeyboardShortcutText(myAction.getAction());
    }

    public boolean isToggleable() {
        return myToggleable;
    }

    @Override
    public boolean isSelected() {
        return myToggled;
    }

    public boolean isKeepMenuOpen() {
        return myKeepMenuOpen;
    }

    private void performAction(int modifiers) {
        @NotNull IdeFocusManager focusManager = CustomFocusManagerImpl.findInstanceByContext(myContext);
        AnAction action = myAction.getAction();
        String id = ActionManager.getInstance().getId(action);
        if (id != null) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats." + id.replace(' ', '.'));
        }

        focusManager.runOnOwnContext(myContext, () -> {
            AWTEvent currentEvent = IdeEventQueue.getInstance().getTrueCurrentEvent();
            AnActionEvent event = new AnActionEvent(
                    currentEvent instanceof InputEvent ? (InputEvent)currentEvent : null,
                    myContext, myPlace, action.getTemplatePresentation().clone(),
                    ActionManager.getInstance(), modifiers, true, false);
            if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
                CustomActionUtil.performActionDumbAwareWithCallbacks(action, event);
            }
        });
    }
}




