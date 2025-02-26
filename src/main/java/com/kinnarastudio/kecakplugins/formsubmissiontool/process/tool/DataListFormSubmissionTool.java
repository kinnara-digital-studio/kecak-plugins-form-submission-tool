package com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.kecakplugins.formsubmissiontool.common.Utils;
import com.kinnarastudio.kecakplugins.formsubmissiontool.exception.DataListGenerationException;
import com.kinnarastudio.kecakplugins.formsubmissiontool.exception.FormGenerationException;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DataListFormSubmissionTool extends DefaultApplicationPlugin {
    public final static String LABEL = "DataList Form Submission Tool";

    @Override
    public String getName() {
        return LABEL;
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
        return DataListFormSubmissionTool.class.getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map properties) {
        LogUtil.info(getClassName(), "execute");

        PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
        WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        try {
            DataList dataList = Utils.generateDataList(getPropertyString("dataListId"), workflowAssignment);
            Map<String, List<String>> filters = getPropertyDataListFilter(this, workflowAssignment);
            getCollectFilters(dataList, filters);
            DataListCollection<Map<String, Object>> dataListCollection = Optional.of(dataList)
                    .map(DataList::getRows)
                    .stream()
                    .flatMap(Collection<Map<String, Object>>::stream)
                    .map(rows -> formatRow(dataList, rows))
                    .collect(Collectors.toCollection(DataListCollection<Map<String, Object>>::new));

            final String formDefId = getPropertyString("formDefId");
            Form form = Utils.getForm(appDefinition, formDefId, new FormData());

            final Triple<String, String, String>[] fieldValues = getValuesMapping();

            for (Map<String, Object> dataListCollectionRow : dataListCollection) {
                LogUtil.info(getClassName(), "============================");

                FormData formData = new FormData() {{
                    if (workflowAssignment != null) {
                        setProcessId(workflowAssignment.getProcessId());
                        setActivityId(workflowAssignment.getActivityId());
                    }
                }};

                for (Triple<String, String, String> mapping : fieldValues) {
                    final String formFieldId = mapping.getLeft();
                    final Element element = FormUtil.findElement(formFieldId, form, formData);
                    if (element == null) {
                        continue;
                    }

                    final String dataListFieldId = mapping.getMiddle();
                    final String dataListFieldValue = Optional.of(dataListFieldId)
                            .filter(Predicate.not(String::isEmpty))
                            .map(dataListCollectionRow::get)
                            .map(String::valueOf)
                            .orElse("");
                    final String fixedValue = mapping.getRight();
                    final String value = dataListFieldId.isEmpty() ? fixedValue : dataListFieldValue;

                    if (formFieldId.equals("id") && !value.isEmpty()) {
                        formData.setPrimaryKeyValue(value);
                    }

                    final String parameterName = FormUtil.getElementParameterName(element);
                    formData.getRequestParams().put(parameterName, new String[]{value});
                }

                formData.getRequestParams().forEach((key, values) -> LogUtil.info(getClassName(), "key [" + key + "] values [" + String.join(";", values) + "]"));

                formData = Utils.submitForm(form, formData, true);

                LogUtil.info(getClassName(), "Form submitted with record id [" + formData.getPrimaryKeyValue() + "]");
                formData.getFormResults().forEach((key, value) -> LogUtil.info(getClassName(), "Form result key [" + key + "] [" + value + "]"));
                formData.getFormErrors().forEach((key, value) -> LogUtil.info(getClassName(), "Form error key [" + key + "] [" + value + "]"));
            }
        } catch (DataListGenerationException | FormGenerationException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }
        return null;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return DataListFormSubmissionTool.class.getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/process/tool/DataListFormSubmissionTool.json", null, true, "/messages/DataListFormSubmissionTool");
    }


    protected void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .stream()
                .flatMap(Arrays::stream)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.setPageSize(Integer.MAX_VALUE);
        dataList.getFilterQueryObjects();
        dataList.setFilters(null);

    }

    Map<String, List<String>> getPropertyDataListFilter(PropertyEditable obj, WorkflowAssignment workflowAssignment) {
        final Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .stream()
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .map(m -> (Map<String, List<String>>) new HashMap<String, List<String>>() {{
                    Optional.of("value")
                            .map(m::get)
                            .map(String::valueOf)
                            .map(s -> Utils.processHashVariable(s, workflowAssignment))
                            .filter(Predicate.not(String::isEmpty))
                            .ifPresent(val -> {
                                String name = String.valueOf(m.get("name"));
                                put(name, Collections.singletonList(val));
                            });
                }})
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> {
                    List<String> result = new ArrayList<>(e1);
                    result.addAll(e2);
                    return result;
                }));

        return filters;
    }

    protected Triple<String, String, String>[] getValuesMapping() {
        return Optional.ofNullable(getProperty("valuesMapping"))
                .map(o -> (Object[]) o)
                .stream()
                .flatMap(Arrays::stream)
                .map(o -> (Map<String, String>) o)
                .map(m -> new ImmutableTriple<>(m.get("formField"), m.get("dataListField"), m.get("constantValue")))
                .toArray(Triple[]::new);
    }

    @Nonnull
    protected Map<String, Object> formatRow(@Nonnull DataList dataList, @Nonnull Map<String, Object> row) {
        return Optional.of(dataList)
                .map(DataList::getColumns)
                .stream()
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .map(DataListColumn::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(s -> s, s -> formatValue(dataList, row, s)));
    }

    @Nonnull
    protected Object formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, String field) {
        Optional<Object> optValue = Optional.of(field)
                .map(row::get);

        if (optValue.isEmpty()) {
            return "";
        }

        Object value = optValue.get();

        return Optional.of(dataList)
                .map(DataList::getColumns)
                .stream()
                .flatMap(Arrays::stream)
                .filter(c -> field.equals(c.getName()))
                .findFirst()
                .flatMap(column -> Optional.of(column)
                        .map(DataListColumn::getFormats)
                        .stream()
                        .flatMap(Collection::stream)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(Try.onFunction(f -> f.format(dataList, column, row, value))))
                .orElse(value.toString());
    }
}
