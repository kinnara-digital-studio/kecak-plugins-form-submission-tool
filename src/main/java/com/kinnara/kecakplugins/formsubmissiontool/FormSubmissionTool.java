package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

public class FormSubmissionTool extends DefaultApplicationPlugin {
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
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        WorkflowManager workflowManager= (WorkflowManager) applicationContext.getBean("workflowManager");
        AppDefinition appDefinition = (AppDefinition) map.get("appDef");

        String formDefId = map.get("formDefId").toString();

        // get primary key from property "primaryKey"
        String primaryKey = Optional.of("primaryKey")
                .map(map::get)
                .map(String::valueOf)
                .map(s -> s.replaceAll("#.+#", ""))
                .filter(s -> !s.isEmpty())

                // or get from property "recordId"
                .orElseGet(() -> Optional.of("recordId")
                        .map(map::get)
                        .map(String::valueOf)
                        .filter(s -> !s.isEmpty())

                        // or get from workflow assignment, originProcessId
                        .orElseGet(() -> Optional.ofNullable(workflowAssignment)
                                .map(WorkflowAssignment::getProcessId)
                                .map(workflowManager::getWorkflowProcessLink)
                                .map(WorkflowProcessLink::getOriginProcessId)
                                .filter(s -> !s.isEmpty())

                                // or get from workflow assignment's process ID
                                .orElseGet(() -> Optional.ofNullable(workflowAssignment)
                                        .map(WorkflowAssignment::getProcessId)
                                        .filter(s -> !s.isEmpty())

                                        // desperately use UUID
                                        .orElseGet(() -> UUID.randomUUID().toString()))));

        final FormData formData = new FormData();
        formData.setPrimaryKeyValue(primaryKey);
        if(workflowAssignment != null) {
            formData.setProcessId(workflowAssignment.getProcessId());
            formData.setActivityId(workflowAssignment.getActivityId());
        }

        Form form = getForm(appDefinition, formDefId, formData);
        if(form == null) {
            LogUtil.warn(getClassName(), "Form [" + formDefId + "] not found");
            return null;
        }

        final Map<String, String> updateWorkflowVariable = new HashMap<>();

        Arrays.stream((Object[]) map.get("fieldValues"))
                .map(o -> (Map<String, Object>)o)
                .forEach(m -> {
                    String field = String.valueOf(m.get("field"));
                    String value = String.valueOf(m.get("value"));

                    Element element = FormUtil.findElement(field, form, formData);
                    if(element == null) {
                        LogUtil.warn(getClassName(), "Element [" + field + "] is not found in form [" + form.getPropertyString("id") + "]");
                        return ;
                    }

                    String parameterName = FormUtil.getElementParameterName(element);

                    formData.getRequestParams().put(parameterName, new String[] { value });

                    String workflowVariableName = element.getPropertyString("workflowVariable");
                    updateWorkflowVariable.put(workflowVariableName, value);
                });

        // fill assignment information
        if(workflowAssignment != null) {
            formData.setActivityId(workflowAssignment.getActivityId());
            formData.setProcessId(workflowAssignment.getProcessId());
        }

        // submit form
        FormData submittedFormData = submitForm(form, formData, false);

        String wfVariableResultPrimaryKey = String.valueOf(map.get("wfVariableResultPrimaryKey"));
        if (!submittedFormData.getFormErrors().isEmpty()) {
            // show validation error message in log
            submittedFormData.getFormErrors().forEach((key, value) -> LogUtil.warn(getClassName(), "Validation Error : form [" + formDefId + "] field [" + key + "] [" + value + "]"));
            if(!wfVariableResultPrimaryKey.isEmpty() && workflowAssignment != null) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, "");
            }
        } else {
            // update workflow variables
            if(!wfVariableResultPrimaryKey.isEmpty() && workflowAssignment != null) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, submittedFormData.getPrimaryKeyValue());
                updateWorkflowVariable.forEach((variable, value) -> workflowManager.processVariable(workflowAssignment.getProcessId(), variable, value));
            }
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

    private boolean isNullOrEmpty(Object value) {
        return value == null || String.valueOf(value).isEmpty();
    }

    private boolean isNotNullOrEmpty(Object value) {
        return !isNullOrEmpty(value);
    }


    private String getOriginProcessIdUsingSql(String processId) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        DataSource dataSource = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");

        String sql = "select originProcessId from wf_process_link where processId = ?";
        try(Connection con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, processId);

            try(ResultSet rs = ps.executeQuery()) {
                if(rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return null;
    }

    protected Form getForm(@Nonnull AppDefinition appDefinition, @Nonnull String formDefId, @Nonnull final FormData formData) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, formData, null, null);
        if(form == null || !form.isAuthorize(formData)) {
            return null;
        }

        FormRowSet rowSet = appService.loadFormData(form, formData.getPrimaryKeyValue());
        if(rowSet != null) {
            rowSet.forEach(row -> row.forEach((key, value) -> {
                Element element = FormUtil.findElement(String.valueOf(key), form, formData);
                if(element != null) {
                    String parameterName = FormUtil.getElementParameterName(element);
                    formData.addRequestParameterValues(parameterName, new String[] { String.valueOf(value) });
                }
            }));
        }

        return form;
    }

    /**
     * Wrap {@link AppService#submitForm(Form, FormData, boolean)}
     *
     * @param form
     * @param formData
     * @param ignoreValidation
     * @return
     */
    protected FormData submitForm(Form form, FormData formData, boolean ignoreValidation) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        String paramName = FormUtil.getElementParameterName(form);
            formData.addRequestParameterValues(paramName + "_SUBMITTED", new String[]{"true"});
        return appService.submitForm(form, formData, ignoreValidation);
    }
}
