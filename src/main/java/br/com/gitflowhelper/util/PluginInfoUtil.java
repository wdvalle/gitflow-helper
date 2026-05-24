package br.com.gitflowhelper.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

public class PluginInfoUtil {

    private static final String PLUGIN_ID = "br.com.gitflowhelper";

    public static String getPluginVersion() {
        PluginId pluginId = PluginId.findId(PLUGIN_ID);
        if (pluginId == null) return null;

        IdeaPluginDescriptor descriptor = PluginManager.getInstance().findEnabledPlugin(pluginId);

        if (descriptor != null) {
            return descriptor.getVersion();
        }
        return "unknown";
    }
}