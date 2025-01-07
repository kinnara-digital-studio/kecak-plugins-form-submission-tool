package com.kinnarastudio.kecakplugins.formsubmissiontool;

import com.kinnarastudio.kecakplugins.formsubmissiontool.exception.NoPrimaryKeyException;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.HiddenField;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.kecak.apps.form.service.FormDataUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class FormSubmissionTool extends DefaultApplicationPlugin {
    @Override
    public String getName() {
        return AppPluginUtil.getMessage("formSubmissionTool.title", getClassName(), "/messages/FormSubmissionTool");
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map map) {
        try {
            ApplicationContext applicationContext = AppUtil.getApplicationContext();
            WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
            PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
            WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
            AppDefinition appDefinition = (AppDefinition) map.get("appDef");

            String formDefId = map.get("formDefId").toString();

            // get primary key from property "primaryKey"
            String primaryKey = Optional.of(getPrimaryKey())
                    .filter(s -> !s.isEmpty())
                    .orElseGet(() -> Optional.of("recordId")
                            .map(map::get)
                            .map(String::valueOf)
                            .filter(s -> !s.isEmpty())

                            // or get from workflow assignment, originProcessId
                            .orElseGet(() -> {
                                final WorkflowProcess workflowProcess = workflowManager.getRunningProcessById(workflowAssignment.getProcessId());

                                String recordId = workflowProcess.getInstanceId();
                                WorkflowProcessLink link = workflowManager.getWorkflowProcessLink(recordId);
                                if (link != null) {
                                    recordId = link.getOriginProcessId();
                                }

                                return recordId;
                            }));

            if (primaryKey == null || primaryKey.isEmpty()) {
                throw new NoPrimaryKeyException("Primary key is not found");
            }

            final FormData formData = new FormData() {{
                setPrimaryKeyValue(primaryKey);

                if (workflowAssignment != null) {
                    setProcessId(workflowAssignment.getProcessId());
                    setActivityId(workflowAssignment.getActivityId());
                }
            }};


            Form form = getForm(appDefinition, formDefId, formData);
            if (form == null) {
                LogUtil.warn(getClassName(), "Form [" + formDefId + "] not found");
                return null;
            }

            final Map<String, String> updateWorkflowVariable = new HashMap<>();

            final Map<String, String> fieldValues = getFieldValues();

            fieldValues.forEach((id, value) -> {
                final Element element = FormUtil.findElement(id, form, formData);
                if (element == null) {
                    return;
                }

                final String parameterName = FormUtil.getElementParameterName(element);
                formData.getRequestParams().put(parameterName, new String[]{value});

                final String workflowVariableName = element.getPropertyString("workflowVariable");
                if (!workflowVariableName.isEmpty()) {
                    updateWorkflowVariable.put(workflowVariableName, value);
                }
            });

            // fill assignment information
            if (workflowAssignment != null) {
                formData.setActivityId(workflowAssignment.getActivityId());
                formData.setProcessId(workflowAssignment.getProcessId());
            }

            // submit form
            final FormData submittedFormData = submitForm(form, formData, true);

            final String wfVariableResultPrimaryKey = String.valueOf(map.get("wfVariableResultPrimaryKey"));
            if (!submittedFormData.getFormErrors().isEmpty()) {
                // show validation error message in log
                submittedFormData.getFormErrors().forEach((key, value) -> LogUtil.warn(getClassName(), "Validation Error : form [" + formDefId + "] field [" + key + "] [" + value + "]"));
                if (!wfVariableResultPrimaryKey.isEmpty() && workflowAssignment != null) {
                    workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, "");
                }
            } else {
                // update workflow variables
                if (!wfVariableResultPrimaryKey.isEmpty() && workflowAssignment != null) {
                    workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, submittedFormData.getPrimaryKeyValue());
                    updateWorkflowVariable.forEach((variable, value) -> workflowManager.processVariable(workflowAssignment.getProcessId(), variable, value));
                }
            }

            return null;
        } catch (NoPrimaryKeyException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
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
        DataSource dataSource = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

        String sql = "select originProcessId from wf_process_link where processId = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, processId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException throwables) {
            LogUtil.error(getClassName(), throwables, "SQL error");
        }

        return null;
    }

    protected Form getForm(@Nonnull AppDefinition appDefinition, @Nonnull String formDefId, @Nonnull final FormData formData) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, formData, null, null);
        if (form == null || !form.isAuthorize(formData)) {
            return null;
        }

        FormRowSet rowSet = appService.loadFormData(form, formData.getPrimaryKeyValue());
        if (rowSet != null) {
            rowSet.forEach(row -> row.forEach((key, value) -> {
                Element element = FormUtil.findElement(String.valueOf(key), form, formData);
                if (element != null) {
                    String parameterName = FormUtil.getElementParameterName(element);
                    formData.addRequestParameterValues(parameterName, new String[]{String.valueOf(value)});
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

    protected Map<String, String> getFieldValues() {
        return Arrays.stream((Object[]) getProperty("fieldValues"))
                .map(o -> (Map<String, Object>) o)
                .filter(m -> m.containsKey("field") && m.containsKey("value"))
                .collect(Collectors.toMap(m -> String.valueOf(m.get("field")), m -> String.valueOf(m.get("value")), (s1, s2) -> s1));
    }

    protected String getPrimaryKey() {
        return getPropertyString("primaryKey");
    }
}
