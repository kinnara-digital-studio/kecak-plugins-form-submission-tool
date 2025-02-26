package com.kinnarastudio.kecakplugins.formsubmissiontool.common;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.kecakplugins.formsubmissiontool.exception.DataListGenerationException;
import com.kinnarastudio.kecakplugins.formsubmissiontool.exception.FormGenerationException;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public final class Utils {
    private Utils() {
    }

    public static DataList generateDataList(String datalistId, WorkflowAssignment workflowAssignment) throws DataListGenerationException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
                .map(dataListService::fromJson)
                .orElseThrow(DataListGenerationException::new);
    }

    @Nonnull
    public static Form getForm(@Nonnull AppDefinition appDefinition, @Nonnull String formDefId, @Nonnull final FormData formData) throws FormGenerationException {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, formData, null, null);
        if (form == null) {
            throw new FormGenerationException("Error generating form [" + formDefId + "]");
        }

        if (!form.isAuthorize(formData)) {
            throw new FormGenerationException("No authorization to load form [" + formDefId + "]");
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
    public static FormData submitForm(Form form, FormData formData, boolean ignoreValidation) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        String paramName = FormUtil.getElementParameterName(form);
        formData.addRequestParameterValues(paramName + "_SUBMITTED", new String[]{"true"});
        return appService.submitForm(form, formData, ignoreValidation);
    }

    public static String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }
}
