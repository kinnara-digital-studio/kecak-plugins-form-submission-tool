package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class FormSubmissionTool extends DefaultApplicationPlugin {
    @Nonnull private Map<String, Form> formCache = new HashMap<String, Form>();

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("formSubmissionTool.title", getClassName(), "/messages/FormSubmissionTool");
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map map) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        Form form = generateForm(map.get("formDefId").toString(), workflowAssignment.getProcessId());
        if(form == null) {
            LogUtil.warn(getClassName(), "Form [" + getPropertyString("formDefId") + "] not found");
            return null;
        }

        FormData formData = Arrays.stream((Object[]) map.get("fieldValues"))
                .map(o -> (Map<String, Object>)o)
                .peek(m -> LogUtil.info(getClassName(), "saving data ["+ m.get("field")+"] ["+m.get("value").toString()+"]"))
                .collect(
                        FormData::new,
                        (fd, m) -> fd.addRequestParameterValues(String.valueOf(m.get("field")), new String[] {String.valueOf(m.get("value"))}),
                        (fd1, fd2) -> fd1.getRequestParams().putAll(fd2.getRequestParams()));
        formData.setPrimaryKeyValue(String.valueOf(map.get("primaryKey")));

        // SUBMIT
        formData = appService.submitForm(form, formData, false);
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        if (!formData.getFormErrors().isEmpty()) {
            formData.getFormErrors().forEach((key, value) -> LogUtil.warn(getClassName(), "Validation Error [" + key + "] [" + value + "]"));
            workflowManager.processVariable(workflowAssignment.getProcessId(), map.get("wfVariableResultPrimaryKey").toString(), "");
        } else {
            workflowManager.processVariable(workflowAssignment.getProcessId(), map.get("wfVariableResultPrimaryKey").toString(), formData.getPrimaryKeyValue());
        }

        return null;
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/FormSubmissionTool.json", null, false, "/messages/FormSubmissionTool");
    }

    /**
     * Construct form from formId
     * @param formDefId
     * @param processId
     * @return
     */
    protected Form generateForm(@Nonnull String formDefId, @Nonnull String processId) {
        return generateForm(formDefId, processId, formCache);
    }

    /**
     * Construct form from formId
     * @param formDefId
     * @param processId
     * @param formCache
     * @return
     */
    @Nullable protected Form generateForm(@Nonnull String formDefId, @Nonnull String processId, @Nonnull Map<String, Form> formCache) {
        // check in cache
        if(formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        FormService formService = (FormService)AppUtil.getApplicationContext().getBean("formService");
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
                formCache.put(formDefId, form);

                return form;
            }
        }
        return null;
    }
}
