package pascal.taie.intellij.gui.webwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefApp;
import pascal.taie.intellij.services.TaieGoToDefService;

import javax.swing.JComponent;
import javax.swing.JLabel;


@Service(Service.Level.PROJECT)
public final class WebWindowService {

    public static final Logger logger = Logger.getInstance(WebWindowService.class);

    private final Project project;

    private final WebWindow webWindow;

    private final JComponent component;

    private String url = "index.html";
    private String flowGraph = "";

    public WebWindowService(Project project) {
        this.project = project;
        if (JBCefApp.isSupported()) {
            webWindow = new WebWindow(project, this);
            component = webWindow.getComponent();
        } else {
            webWindow = null;
            component = new JLabel("CEF is not supported on this platform");
        }
    }

    public JComponent getComponent() {
        return component;
    }

    public synchronized void reload(String url, String flowGraph) {
        this.url = url;
        this.flowGraph = flowGraph;
        if (webWindow != null) {
            webWindow.reload();
        }
    }

    public String getUrl() {
        return url;
    }

    public String getFlowGraph() {
        return flowGraph;
    }

    public static class JavaQuery {
        public String type;
        public String data;
    }

    public String getJavaQueryResult(String request) {
        ObjectMapper mapper = new ObjectMapper();
        JavaQuery query;
        try {
            query = mapper.readValue(request, JavaQuery.class);
        } catch (Exception e) {
            logger.error("Failed to parse query", e);
            return null;
        }
        if (query.type.equals("getFlowGraph")) {
            return getFlowGraph();
        } else if (query.type.equals("goToDef")) {
            TaieGoToDefService service = project.getService(TaieGoToDefService.class);
            if (service.goToDef(query.data)) {
                return "success";
            } else {
                return "failed";
            }
        } else {
            logger.warn("Unknown query: " + query.type);
            return "Unknown query: " + query.type;
        }
    }
}
