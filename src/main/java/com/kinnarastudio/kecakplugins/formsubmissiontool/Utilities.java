package com.kinnarastudio.kecakplugins.formsubmissiontool;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

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

}
