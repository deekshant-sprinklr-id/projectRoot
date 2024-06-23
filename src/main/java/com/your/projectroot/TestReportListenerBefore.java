package com.your.projectroot;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestStatusListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;

public class TestReportListenerBefore extends TestStatusListener {
    private static final Logger logger = Logger.getInstance(TestReportListenerBefore.class);
    @Override
    public void testSuiteFinished(AbstractTestProxy root) {
        Project project = getCurrentProject();
        if (project != null) {
            try (FileWriter writer = new FileWriter(project.getBasePath() + "/test-report1.txt")) {
                writer.write("Test Report\n");
                writer.write("===========\n\n");
                generateReport(root, writer);
            } catch (IOException e) {
                logger.info("Exception encountered in report generation");
            }
        }
    }

    public void generateReport(AbstractTestProxy root, FileWriter writer) throws IOException {
        Stack<AbstractTestProxy> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            AbstractTestProxy current = stack.pop();

            if (current.isLeaf()) {
                writer.write(current.getName() + ": " + (current.isPassed() ? "PASSED" : "FAILED") + "\n");
            }

            for (AbstractTestProxy child : current.getChildren()) {
                stack.push(child);
            }
        }
    }

    private Project getCurrentProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;  // Return the first open project for simplicity
    }
}