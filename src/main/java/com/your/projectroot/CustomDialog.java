package com.your.projectroot;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CustomDialog extends DialogWrapper {
    private JTextField depthField;
    private JCheckBox checkPreviousCommitCheckBox;

    public CustomDialog() {
        super(true); // use current window as parent
        init();
        setTitle("Input Depth Level and Check Previous Commit");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());

        depthField = new JTextField();
        checkPreviousCommitCheckBox = new JCheckBox("Check Previous Commit");

        JPanel inputPanel = new JPanel(new GridLayout(2, 1));
        inputPanel.add(new JLabel("Enter the depth level for the method usage search:"));
        inputPanel.add(depthField);
        inputPanel.add(checkPreviousCommitCheckBox, GroupLayout.Alignment.CENTER);

        dialogPanel.add(inputPanel, BorderLayout.CENTER);

        return dialogPanel;
    }

    public String getDepth() {
        return depthField.getText();
    }

    public boolean isCheckPreviousCommit() {
        return checkPreviousCommitCheckBox.isSelected();
    }
}

