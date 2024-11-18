package pascal.taie.intellij.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class TaieGoToDefService {

    public static final Logger logger = Logger.getInstance(TaieGoToDefService.class);

    private final Project project;

    public TaieGoToDefService(Project project) {
        this.project = project;
    }

    public static class GoToDefQueryData {
        public String groupType;
        public String signature;
    }

    public boolean goToDef(String signature) {
        ObjectMapper mapper = new ObjectMapper();
        GoToDefQueryData queryData;
        try {
            queryData = mapper.readValue(signature, GoToDefQueryData.class);
        } catch (Exception e) {
            logger.error("Failed to parse query", e);
            return false;
        }
        PsiElement element = switch (queryData.groupType) {
            case "package" -> findPackageDef(queryData.signature);
            case "class" -> findClassDef(queryData.signature);
            case "method" -> findMethodDef(queryData.signature);
            default -> null;
        };
        if (element == null) {
            logger.info("Definition not found");
            return false;
        } else {
            ApplicationManager.getApplication().invokeLater(() -> NavigationUtil.activateFileWithPsiElement(element));
            return true;
        }
    }

    private PsiElement findPackageDef(String signature) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PsiElement>)
                        () -> JavaPsiFacade.getInstance(project)
                                .findPackage(signature));
    }

    private PsiElement findClassDef(String signature) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PsiElement>)
                        () -> JavaPsiFacade.getInstance(project)
                                .findClass(signature, GlobalSearchScope.allScope(project)));
    }

    private PsiElement findMethodDef(String signature) {
        // <CLASS_NAME: RET_TYPE name(PARAMS..)>
        Pattern pattern = Pattern.compile("<([^:]+): (\\S+) ([^()]+)\\([^()]*\\)>");
        Matcher matcher = pattern.matcher(signature);
        if (!matcher.matches()) {
            return null;
        }
        String className = matcher.group(1);
        String methodName = matcher.group(3);
        PsiClass psiClass = (PsiClass) findClassDef(className);
        if (psiClass == null) {
            return null;
        }
        if (methodName.equals("<init>")) {
            return Arrays.stream(psiClass.getConstructors())
                    .findFirst()
                    .orElse(null);
        } else if (methodName.equals("<clinit>")) {
            return Arrays.stream(psiClass.getInitializers())
                    .findFirst()
                    .orElse(null);
        } else {
            return Arrays.stream(psiClass.findMethodsByName(methodName, true))
                    .findFirst()
                    .orElse(null);
        }
    }

}
