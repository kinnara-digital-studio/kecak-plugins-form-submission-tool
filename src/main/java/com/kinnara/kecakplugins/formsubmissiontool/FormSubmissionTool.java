package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

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
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");

        String formDefId = map.get("formDefId").toString();
        Form form = Utilities.generateForm(formDefId, workflowAssignment.getProcessId());
        if(form == null) {
            LogUtil.warn(getClassName(), "Form [" + formDefId + "] not found");
            return null;
        }

        String primaryKey = String.valueOf(map.get("primaryKey")).replaceAll("#.+#", ""); // cleanup hash variables
        if(primaryKey.isEmpty()) {
            LogUtil.info(getClassName(), "Primary Key is not specified, use process id [" + workflowAssignment.getProcessId() + "] as key");
            primaryKey = workflowAssignment.getProcessId();
        }

        final FormData storingFormData = Arrays.stream((Object[]) map.get("fieldValues"))
                .map(o -> (Map<String, Object>)o)
                .collect(
                        FormData::new,
                        (fd, m) -> fd.addRequestParameterValues(String.valueOf(m.get("field")), new String[] {String.valueOf(m.get("value"))}),
                        (fd1, fd2) -> fd1.getRequestParams().putAll(fd2.getRequestParams()));
        storingFormData.setPrimaryKeyValue(primaryKey);

        FormData loadingFormData = new FormData();
        loadingFormData.setPrimaryKeyValue(primaryKey);

        Map<String, Object> propertyLoadBinder;
        Plugin pluginLoadBinder;
        if((propertyLoadBinder = (Map<String, Object>)map.get("loadBinder")) != null
                && (pluginLoadBinder = pluginManager.getPlugin(String.valueOf(propertyLoadBinder.get(FormUtil.PROPERTY_CLASS_NAME)))) != null
                && !propertyLoadBinder.isEmpty()) {
            // TODO : do we really need to implement [loadBinder] property?
            LogUtil.info(getClassName(), "Using overwritten Load Binder ["+pluginLoadBinder.getName()+"]");
            try {
                ((PropertyEditable)pluginLoadBinder).setProperties((Map<String, Object>) propertyLoadBinder.get(FormUtil.PROPERTY_PROPERTIES));
                form.setLoadBinder((FormLoadBinder) pluginLoadBinder);

                // load previous data
                formService.executeFormLoadBinders(form, loadingFormData).getLoadBinderData(form)
                        .stream()
                        .map(FormRow::entrySet)
                        .flatMap(Collection::stream)
                        .filter(e -> e.getKey() != null && !e.getKey().toString().isEmpty() && e.getValue() != null && !e.getValue().toString().isEmpty())

                        // ignore built-in field
                        .filter(e -> !FormUtil.PROPERTY_ID.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_DATE_CREATED.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_CREATED_BY.equalsIgnoreCase(e.getKey().toString()))

                        // get field which in not in fieldValues
                        .filter(e -> !storingFormData.getRequestParams().containsKey(e.getKey().toString()))

                        // apply data from load binder
                        .forEach(e -> storingFormData.addRequestParameterValues(e.getKey().toString(), new String[]{e.getValue().toString()}));
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Error configuring load binder ["+propertyLoadBinder.get(FormUtil.PROPERTY_CLASS_NAME)+"]");
            }
        } else {
            formService.executeFormLoadBinders(form, loadingFormData).getLoadBinderData(form)
                    .stream()
                    .map(FormRow::entrySet)
                    .flatMap(Collection::stream)
                    .filter(e -> e.getKey() != null && !e.getKey().toString().isEmpty() && e.getValue() != null && !e.getValue().toString().isEmpty())
                    .filter(e -> !FormUtil.PROPERTY_ID.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_DATE_CREATED.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_CREATED_BY.equalsIgnoreCase(e.getKey().toString()))
                    .filter(e -> !storingFormData.getRequestParams().containsKey(e.getKey().toString()))
                    .forEach(e -> storingFormData.addRequestParameterValues(e.getKey().toString(), new String[]{e.getValue().toString()}));
        }

        // fill assignment information
        storingFormData.setActivityId(workflowAssignment.getActivityId());
        storingFormData.setProcessId(workflowAssignment.getProcessId());

        // submit form
        appService.submitForm(form, storingFormData, false);

        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        String wfVariableResultPrimaryKey = map.get("wfVariableResultPrimaryKey").toString();
        if (!storingFormData.getFormErrors().isEmpty()) {
            storingFormData.getFormErrors().forEach((key, value) -> LogUtil.warn(getClassName(), "Validation Error : form [" + formDefId + "] field [" + key + "] [" + value + "]"));
            if(!wfVariableResultPrimaryKey.isEmpty())
                workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, "");
        } else {
            if(!wfVariableResultPrimaryKey.isEmpty())
                workflowManager.processVariable(workflowAssignment.getProcessId(), wfVariableResultPrimaryKey, storingFormData.getPrimaryKeyValue());
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
}
