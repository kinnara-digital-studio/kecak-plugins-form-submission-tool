package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
        AppService appService = (AppService) applicationContext.getBean("appService");
        FormService formService = (FormService) applicationContext.getBean("formService");
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        WorkflowManager workflowManager= (WorkflowManager) applicationContext.getBean("workflowManager");
        AppDefinition appDefinition = (AppDefinition) map.get("appDef");

        String formDefId = map.get("formDefId").toString();
        FormData loadingFormData = new FormData();
        if(workflowAssignment != null) {
            loadingFormData.setProcessId(workflowAssignment.getProcessId());
            loadingFormData.setActivityId(workflowAssignment.getActivityId());
        }

        Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, loadingFormData, null, null);
        if(form == null) {
            LogUtil.warn(getClassName(), "Form [" + formDefId + "] not found");
            return null;
        }

        String primaryKey = Optional.ofNullable(map.get("primaryKey"))
                .map(String::valueOf)
                .map(s -> s.replaceAll("#.+#", ""))
                .filter(s -> !s.isEmpty())
                .orElse(Optional.ofNullable(map.get("recordId"))
                        .map(String::valueOf)
                        .filter(s -> !s.isEmpty())
                        .orElse(Optional.ofNullable(workflowAssignment)
                                .map(WorkflowAssignment::getProcessId)
                                .map(appService::getOriginProcessId)
                                .orElse("")));

        final FormData storingFormData = Arrays.stream((Object[]) map.get("fieldValues"))
                .map(o -> (Map<String, Object>)o)
                .collect(
                        FormData::new,
                        (fd, m) -> fd.addRequestParameterValues(String.valueOf(m.get("field")), new String[] {String.valueOf(m.get("value"))}),
                        (fd1, fd2) -> fd1.getRequestParams().putAll(fd2.getRequestParams()));

        storingFormData.setPrimaryKeyValue(primaryKey);
        if(workflowAssignment != null) {
            storingFormData.setActivityId(workflowAssignment.getActivityId());
            storingFormData.setProcessId(workflowAssignment.getProcessId());
        }

        // filter sections by permissions
        // IMPORTANT !!!!! section removing does not work for subform
//        form.getChildren().removeIf(element -> {
//            Section section = (Section) element;
//            UserviewPermission permission = Utilities.getPermissionObject(section, storingFormData);
//            return permission != null && !permission.isAuthorize();
//        });

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
                        .filter(e -> isNotNullOrEmpty(e.getKey()) && isNotNullOrEmpty(e.getValue()))

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
            Optional.ofNullable(formService.executeFormLoadBinders(form, loadingFormData))
                    .map(fd -> fd.getLoadBinderData(form))
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(FormRow::entrySet)
                    .flatMap(Collection::stream)
                    .filter(e -> isNotNullOrEmpty(e.getKey()) && isNotNullOrEmpty(e.getValue()))
                    .filter(e -> !FormUtil.PROPERTY_ID.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_DATE_CREATED.equalsIgnoreCase(e.getKey().toString()) && !FormUtil.PROPERTY_CREATED_BY.equalsIgnoreCase(e.getKey().toString()))
                    .filter(e -> !storingFormData.getRequestParams().containsKey(e.getKey().toString()))
                    .forEach(e -> storingFormData.addRequestParameterValues(e.getKey().toString(), new String[]{e.getValue().toString()}));
        }

        // fill assignment information
        storingFormData.setActivityId(workflowAssignment.getActivityId());
        storingFormData.setProcessId(workflowAssignment.getProcessId());

        // submit form
        appService.submitForm(form, storingFormData, false);

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

    private boolean isNullOrEmpty(Object value) {
        return value == null || String.valueOf(value).isEmpty();
    }

    private boolean isNotNullOrEmpty(Object value) {
        return !isNullOrEmpty(value);
    }
}
