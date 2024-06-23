//package com.your.projectroot;
//
//import com.intellij.openapi.actionSystem.ActionPromoter;
//import com.intellij.openapi.actionSystem.AnAction;
//import com.intellij.openapi.actionSystem.DataContext;
//import com.intellij.util.containers.ContainerUtil;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class MasterAction implements ActionPromoter {
//
//    @Override
//    public List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
//        List<AnAction> prioritizedActions = new ArrayList<>();
//
//        // Find and add actions in the desired order
//        AnAction runChangeTrackingAction = ContainerUtil.findInstance(actions, RunChangeTrackingAction.class);
//        if (runChangeTrackingAction != null) {
//            prioritizedActions.add(runChangeTrackingAction);
//        }
//
//        AnAction runStashedChangeTrackingAction = ContainerUtil.findInstance(actions, RunStashedChangeTrackingAction.class);
//        if (runStashedChangeTrackingAction != null) {
//            prioritizedActions.add(runStashedChangeTrackingAction);
//        }
//
//        AnAction poppingAction = ContainerUtil.findInstance(actions, PoppingAction.class);
//        if (poppingAction != null) {
//            prioritizedActions.add(poppingAction);
//        }
//
//        return prioritizedActions;
//    }
//}
//
//
//
//
//
//
//
////import com.intellij.openapi.actionSystem.*;
////import com.intellij.openapi.project.Project;
////import org.jetbrains.annotations.NotNull;
////
////public class MasterAction extends AnAction {
////    @Override
////    public void actionPerformed(@NotNull AnActionEvent e) {
////        Project project = e.getProject();
////        if (project != null) {
////            ActionManager actionManager = ActionManager.getInstance();
////
////            // Define the sequence of action IDs
////            String[] actionIds = new String[]{
////                    "com.your.projectroot.RunChangeTrackingAction",
////                    "com.your.projectroot.RunStashedChangeTrackingAction",
////                    "com.your.projectroot.PoppingAction"
////            };
////
////            // Execute each action in sequence
////            DataContext dataContext = e.getDataContext();
////            for (String actionId : actionIds) {
////                AnAction action = actionManager.getAction(actionId);
////                if (action != null) {
////                    AnActionEvent actionEvent = AnActionEvent.createFromAnAction(
////                            action, null, ActionPlaces.UNKNOWN, dataContext);
////                    System.out.println("Action:  "+actionEvent);
////                    action.actionPerformed(actionEvent);
////                    System.out.println("Action Peformed");
////                    System.out.println(action.getActionUpdateThread());
////                }
////            }
////        }
////    }
////}
