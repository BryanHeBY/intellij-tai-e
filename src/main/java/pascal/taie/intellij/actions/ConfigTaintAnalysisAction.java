package pascal.taie.intellij.actions;

import com.google.protobuf.Message;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class ConfigTaintAnalysisAction extends DefaultConfig {

    @Override
    void actionPerformedImpl(@NotNull AnActionEvent e, @NotNull Project project) {
        Messages.showWarningDialog("Coming soon!", "Not Implemented Yet!");
    }
}
