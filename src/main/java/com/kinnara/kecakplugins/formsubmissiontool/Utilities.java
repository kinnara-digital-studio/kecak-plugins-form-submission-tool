package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.Section;
import org.joget.apps.form.service.FormService;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Utilities {
    @Nonnull private static Map<String, Form> formCache = new HashMap<>();

    /**
     * Construct form from formId
     * @param formDefId
     * @param processId
     * @return
     */
    public static Form generateForm(@Nonnull String formDefId, @Nonnull String processId) {
        return generateForm(formDefId, processId, formCache);
    }

    /**
     * Construct form from formId
     * @param formDefId
     * @param processId
     * @param formCache
     * @return
     */
    @Nullable
    public static Form generateForm(@Nonnull String formDefId, @Nonnull String processId, @Nonnull Map<String, Form> formCache) {
        // check in cache
//        if(formCache.containsKey(formDefId))
//            return formCache.get(formDefId);

        // proceed without cache
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        Form form;
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null && !formDefId.isEmpty()) {
            FormDefinitionDao formDefinitionDao = (FormDefinitionDao)AppUtil.getApplicationContext().getBean("formDefinitionDao");
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                FormData formData = new FormData();
                String json = formDef.getJson();
                if (!processId.isEmpty()) {
                    formData.setProcessId(processId);
                    WorkflowManager wm = (WorkflowManager)AppUtil.getApplicationContext().getBean("workflowManager");
                    WorkflowAssignment wfAssignment = wm.getAssignmentByProcess(processId);
                    json = AppUtil.processHashVariable(json, wfAssignment, "json", null);
                }

                form = (Form)formService.createElementFromJson(json);
                // put in cache if possible
//                formCache.put(formDefId, form);

                return form;
            }
        }
        return null;
    }

    public static UserviewPermission getPermissionObject(Section section, FormData formData) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        DirectoryManager directoryManager = (DirectoryManager) applicationContext.getBean("directoryManager");

        Map<String, Object> propertyPermission = (Map<String, Object>)section.getProperty("permission");
        if(propertyPermission == null)
            return null;

        String className = (String)propertyPermission.get("className");
        Map<String, Object> properties = (Map<String, Object>)propertyPermission.get("properties");

        UserviewPermission permission = (UserviewPermission) pluginManager.getPlugin(className);
        if(permission == null)
            return null;

        if(properties != null)
            permission.setProperties(properties);

        permission.setFormData(formData);

        String username = WorkflowUtil.getCurrentUsername();
        User user = directoryManager.getUserById(username);
        if(user == null) {
            LogUtil.warn(Utilities.class.getName(), "Username ["+username+"] is not listed in directory manager");
        }

        permission.setCurrentUser(user);

        return permission;
    }

    /**
     * Stream element children
     *
     * @param element
     * @return
     */
    @Nonnull
    public static Stream<Element> elementStream(@Nonnull Element element, FormData formData) {
        if (!element.isAuthorize(formData)) {
            return Stream.empty();
        }

        Stream<Element> stream = Stream.of(element);
        for (Element child : element.getChildren()) {
            stream = Stream.concat(stream, elementStream(child, formData));
        }
        return stream;
    }
}
