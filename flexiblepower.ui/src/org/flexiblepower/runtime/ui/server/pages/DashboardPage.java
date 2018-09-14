package org.flexiblepower.runtime.ui.server.pages;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.http.HttpServlet;

import org.flexiblepower.runtime.ui.server.widgets.AbstractWidgetManager;
import org.flexiblepower.runtime.ui.server.widgets.WidgetRegistration;
import org.flexiblepower.runtime.ui.server.widgets.WidgetRegistry;
import org.flexiblepower.ui.Widget;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPolicy = ConfigurationPolicy.OPTIONAL,
           immediate = true,
           service = { Widget.class },
           property = { "widget.type=full", "widget.name=dashboard", "widget.ranking:Integer=1000000" })
@Designate(ocd = DashboardPage.Config.class)
public class DashboardPage extends AbstractWidgetManager implements Widget {
    private static final Logger logger = LoggerFactory.getLogger(DashboardPage.class);

    @ObjectClassDefinition(description = "Configuration of the Dashboard Servlet", name = "Dashboard Configuration")
    public @interface Config {
        @AttributeDefinition(type = AttributeType.LONG,
                             description = "Expiration time of static content (in seconds)",
                             name = "Expiration time",
                             options = {
                                         @Option(label = "No caching", value = "0"),
                                         @Option(label = "A minute", value = "60"),
                                         @Option(label = "An hour", value = "3600"),
                                         @Option(label = "A day", value = "86400"),
                                         @Option(label = "A year", value = "31536000")
                             },
                             required = false)
        long expireTime() default 31536000;
    }

    private long expirationTime = 31536000000L;

    @Activate
    public void activate(final Config config) {
        // TODO: pretty print a configuration properties
        logger.trace("Entering activate, properties = " + config);
        expirationTime = config.expireTime() * 1000;
        logger.trace("Leaving activate");
    }

    @Override
    @Reference(cardinality = ReferenceCardinality.MULTIPLE,
               policy = ReferencePolicy.DYNAMIC,
               target = "(!(" + WidgetRegistry.KEY_TYPE
                        + "="
                        + WidgetRegistry.VALUE_TYPE_FULL
                        + "))")
    public synchronized void addWidget(Widget widget, Map<String, Object> properties) {
        super.addWidget(widget, properties);
        notifyAll();
    }

    @Override
    public synchronized void removeWidget(Widget widget) {
        super.removeWidget(widget);
        notifyAll();
    }

    @Override
    public String createPath(WidgetRegistration registration) {
        return "/widget/" + registration.getId();
    }

    @Override
    public HttpServlet createServlet(WidgetRegistration registration) {
        return new DashboardWidgetServlet(registration, expirationTime);
    }

    // Full-size widget functions

    @Override
    public String getTitle(Locale locale) {
        return "Dashboard";
    }

    public synchronized SortedMap<Integer, String> getWidgets(Locale locale, Integer[] currentWidgets) {
        logger.trace("Entering getWidgets, locale = {}, currentWidgets = {}", locale, currentWidgets);
        SortedMap<Integer, String> widgetInfo = getWidgetInfo(locale);

        if (Arrays.equals(widgetInfo.keySet().toArray(new Integer[widgetInfo.size()]), currentWidgets)) {
            logger.trace("No change, waiting...");
            try {
                wait(30000);
            } catch (InterruptedException ex) {
                // Expected
            }

            widgetInfo = getWidgetInfo(locale);
        }

        logger.trace("Leaving getWidgets, result = {}", widgetInfo);
        return widgetInfo;
    }

    private SortedMap<Integer, String> getWidgetInfo(Locale locale) {
        SortedMap<Integer, String> widgetInfo = new TreeMap<Integer, String>();
        for (WidgetRegistration reg : getRegistrations()) {
            widgetInfo.put(reg.getId(), reg.getWidget().getTitle(locale));
        }
        return widgetInfo;
    }
}
