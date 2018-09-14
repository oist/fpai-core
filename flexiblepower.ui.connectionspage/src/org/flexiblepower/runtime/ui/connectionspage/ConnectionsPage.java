package org.flexiblepower.runtime.ui.connectionspage;

import java.util.Hashtable;

import javax.servlet.Servlet;

import org.flexiblepower.messaging.ConnectionManager;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(configurationPolicy = ConfigurationPolicy.OPTIONAL)
@Designate(ocd = ConnectionsPage.Config.class)
public class ConnectionsPage {
    @ObjectClassDefinition(description = "Configuration for the ConnectionManager widgets",
                           name = "ConnectionManager UI Configuration")
    public @interface Config {
        @AttributeDefinition(type = AttributeType.BOOLEAN,
                             description = "Should the plugin be shown in the felix dashboard?",
                             required = false)
        boolean felixPluginActive() default true;

        @AttributeDefinition(type = AttributeType.BOOLEAN,
                             defaultValue = "false",
                             description = "Should the plugin be shown in the FPAI dashboard?",
                             required = false)
        boolean dashboardWidgetActive() default true;
    }

    private ConnectionManager connectionManager;
    private ServiceRegistration<Widget> dashboardWidgetRegistration;
    private ServiceRegistration<Servlet> felixPluginRegistration;

    @Reference
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Activate
    public void activate(BundleContext context, final Config config) {

        if (config.dashboardWidgetActive()) {
            try {
                DashboardWidget dashboardWidget = new DashboardWidget(connectionManager, context);

                Hashtable<String, Object> widgetProperties = new Hashtable<String, Object>();
                widgetProperties.put("widget.type", "full");
                widgetProperties.put("widget.name", "connection-manager");
                dashboardWidgetRegistration = context.registerService(Widget.class, dashboardWidget, widgetProperties);
            } catch (NoClassDefFoundError error) {
                // this could happen if there is no FPAI dashboard loaded, just ignore the start then
            }
        }

        if (config.felixPluginActive()) {
            try {
                FelixPlugin felixPlugin = new FelixPlugin(connectionManager, context);

                Hashtable<String, Object> widgetProperties = new Hashtable<String, Object>();
                widgetProperties.put("felix.webconsole.category", "FPAI");
                widgetProperties.put("felix.webconsole.label", felixPlugin.getLabel());
                widgetProperties.put("felix.webconsole.title", felixPlugin.getTitle());
                felixPluginRegistration = context.registerService(Servlet.class, felixPlugin, widgetProperties);
            } catch (NoClassDefFoundError error) {
                // this could happen if there is no felix dashboard loaded, just ignore the start then
            }
        }
    }

    @Deactivate
    public void deactivate() {
        if (dashboardWidgetRegistration != null) {
            dashboardWidgetRegistration.unregister();
            dashboardWidgetRegistration = null;
        }

        if (felixPluginRegistration != null) {
            felixPluginRegistration.unregister();
            felixPluginRegistration = null;
        }
    }
}
