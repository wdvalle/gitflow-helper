package br.com.gitflowhelper.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;

@Deprecated
public class PluginInfoUtil {

    private static final String PLUGIN_ID = "br.com.gitflowhelper";

    public static String getPluginVersion() {
//        PluginId pluginId = PluginId.findId(PLUGIN_ID);
//        if (pluginId == null) return null;
//
//        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
//
//        if (descriptor != null) {
//            return descriptor.getVersion();
//        }
        return "unknown";
    }
}