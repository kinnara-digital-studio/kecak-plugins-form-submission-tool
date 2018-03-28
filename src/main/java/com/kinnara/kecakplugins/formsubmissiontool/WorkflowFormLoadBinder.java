package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

/**
 * 
 * @author aristo
 * 
 * Default form binder for element {@link FormSubmissionTool}
 * Load data from another form
 *
 */
public class WorkflowFormLoadBinder extends WorkflowFormBinder {
	public FormRowSet load(Element element, String originPrimaryKey, FormData formData) {
		WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
		WorkflowAssignment workflowAssignment = workflowManager.getAssignmentByProcess(formData.getProcessId());
		String primaryKey = getPropertyString("primaryKey").isEmpty() ? originPrimaryKey : AppUtil.processHashVariable(getPropertyString("primaryKey"), workflowAssignment, null, null);
		formData.setPrimaryKeyValue(primaryKey);

		String formDefId = getPropertyString("formDefId");
		Form form = Utilities.generateForm(formDefId, formData.getProcessId());
		if(form == null) {
			LogUtil.warn(getClassName(), "Form [" + formDefId + "] not found");
			return new FormRowSet();
		}
		return super.load(form, primaryKey, formData);
	}

	public String getLabel() {
		return getName();
	}

	public String getClassName() {
		return getClass().getName();
	}

	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClassName(), "/properties/WorkflowFormLoadBinder.json", null, true, "/messages/WorkflowFormLoadBinder");
	}

	public String getName() {
		return AppPluginUtil.getMessage("workflowFormLoadBinder.title", getClassName(), "/messages/WorkflowFormLoadBinder");
	}

	public String getVersion() {
		return getClass().getPackage().getImplementationVersion();
	}

	public String getDescription() {
		return getClass().getPackage().getImplementationTitle();
	}
}
