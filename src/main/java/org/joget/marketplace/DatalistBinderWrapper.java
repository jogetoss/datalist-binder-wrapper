package org.joget.marketplace;

import java.util.HashMap;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinderDefault;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;

public class DatalistBinderWrapper extends DataListBinderDefault{
    private static String MESSAGE_PATH = "messages/datalistBinderWrapper";

    @Override
    public String getName() {
        return "Datalist Binder Wrapper";
    }

    @Override
    public String getVersion() {
        return "8.0.2";
    }

    @Override
    public String getDescription() {
        return "Datalist Binder Wrapper";
    }

    @Override
    public String getLabel() {
        return "Datalist Binder Wrapper";
    }


    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{getLabel(), appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/datalistBinderWrapper.json", arguments, true, MESSAGE_PATH);
        return json;
    }

    protected Object executeScript(String script, Map properties) {
        Object result = null;
        try {
            LogUtil.debug(getClass().getName(), "Executing script " + script);
            result = AppPluginUtil.executeScript(script, properties);
            return result;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error executing script");
            return null;
        }
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    /**
     * Holds the configured inner list binder plugin together with the property
     * map used to configure it, so both can be reused for a single delegated call
     * without re-resolving the plugin from scratch.
     */
    private static class ResolvedBinder {
        DataListBinderDefault plugin;
        Map propertiesMap;
    }

    /**
     * Resolves the "datalistBinder" property into an actual plugin instance and
     * applies its default properties (merged with whatever was configured on this
     * wrapper). Centralizes logic previously duplicated across getColumns(),
     * getPrimaryKeyColumnName(), getData() and getDataTotalRowCount().
     */
    private ResolvedBinder resolveInnerBinder() {
        Map binderProps = getProperties();
        Object datalistBinder = binderProps.get("datalistBinder");

        if (!(datalistBinder instanceof Map)) {
            return null;
        }

        Map fvMap = (Map) datalistBinder;
        Object classNameObj = fvMap.get("className");
        if (classNameObj == null || classNameObj.toString().isEmpty()) {
            return null;
        }

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        String className = classNameObj.toString();
        DataListBinderDefault datalistBinderPlugin = (DataListBinderDefault) pluginManager.getPlugin(className);
        if (datalistBinderPlugin == null) {
            return null;
        }

        Map propertiesMap = new HashMap();
        propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) datalistBinderPlugin, (Map) fvMap.get("properties"), null, null));

        if (datalistBinderPlugin instanceof PropertyEditable) {
            ((PropertyEditable) datalistBinderPlugin).setProperties(propertiesMap);
        }

        ResolvedBinder resolved = new ResolvedBinder();
        resolved.plugin = datalistBinderPlugin;
        resolved.propertiesMap = propertiesMap;
        return resolved;
    }

    @Override
    public DataListColumn[] getColumns() {
        ResolvedBinder resolved = resolveInnerBinder();
        return resolved != null ? resolved.plugin.getColumns() : null;
    }

    @Override
    public String getPrimaryKeyColumnName() {
        ResolvedBinder resolved = resolveInnerBinder();
        return resolved != null ? resolved.plugin.getPrimaryKeyColumnName() : null;
    }

    @Override
    public DataListCollection getData(DataList dl, Map map, DataListFilterQueryObject[] dlfqos, String string, Boolean bln, Integer intgr, Integer intgr1) {
        ResolvedBinder resolved = resolveInnerBinder();
        if (resolved == null) {
            return null;
        }

        DataListCollection data = resolved.plugin.getData(dl, resolved.propertiesMap, dlfqos, string, bln, intgr, intgr1);

        Map scriptProperties = new HashMap();
        scriptProperties.put("data", data);
        scriptProperties.put("columns", resolved.plugin.getColumns());

        if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
            LogUtil.info(DatalistBinderWrapper.class.getName(), "Data from binder: " + data);
        }

        String script = (String) getProperties().get("script");

        Object scriptResult = executeScript(script, scriptProperties);
        DataListCollection formattedData = data;
        if (scriptResult instanceof DataListCollection) {
            formattedData = (DataListCollection) scriptResult;
        } else if (scriptResult != null) {
            LogUtil.warn(getClass().getName(), "Script did not return a DataListCollection (got " + scriptResult.getClass().getName() + "); falling back to the unmodified data from the binder");
        }

        if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
            LogUtil.info(DatalistBinderWrapper.class.getName(), "Data after script: " + formattedData);
        }

        return formattedData;
    }

    @Override
    public int getDataTotalRowCount(DataList dl, Map map, DataListFilterQueryObject[] dlfqos) {
        if ("true".equalsIgnoreCase(getPropertyString("useFormattedDataCount"))) {
            DataListCollection formattedData = getData(dl, map, dlfqos, null, true, null, null);
            return formattedData != null ? formattedData.size() : 0;
        }

        ResolvedBinder resolved = resolveInnerBinder();
        if (resolved == null) {
            return 0;
        }

        return resolved.plugin.getDataTotalRowCount(dl, resolved.propertiesMap, dlfqos);
    }

}
