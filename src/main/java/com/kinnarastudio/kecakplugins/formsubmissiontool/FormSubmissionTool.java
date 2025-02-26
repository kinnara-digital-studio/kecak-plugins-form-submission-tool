package com.kinnarastudio.kecakplugins.formsubmissiontool;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.PluginManager;

import java.util.*;

/**
 * Use {@link com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool.FormSubmissionTool}
 */
@Deprecated
public class FormSubmissionTool extends com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool.FormSubmissionTool {
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
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return FormSubmissionTool.class.getName();
    }
}
