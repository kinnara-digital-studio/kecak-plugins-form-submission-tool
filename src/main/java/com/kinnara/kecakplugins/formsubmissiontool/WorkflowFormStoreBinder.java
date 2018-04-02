package com.kinnara.kecakplugins.formsubmissiontool;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
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
public class WorkflowFormStoreBinder extends WorkflowFormBinder {
	@Override
	public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
		return super.store(element, rows, formData);
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
