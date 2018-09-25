package org.flexiblepower.runtime.messaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.ConnectionManager;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.MessageListener;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, configurationPolicy = ConfigurationPolicy.OPTIONAL)
@Designate(ocd = ConnectionManagerImpl.Config.class)
public class ConnectionManagerImpl implements ConnectionManager {
    private static final String KEY_ACTIVE_CONNECTIONS = "active.connections";
    private static final String KEY_AUTOCONNECT = "autoconnect";
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManagerImpl.class);

    @ObjectClassDefinition(name = "Connection Manager Configuration",
                           description = "The ConnectionManager is responsible for wiring 2 ports for 2 different endpoints to each other."
                                         + "Warning: any modifications during runtime won't be activated right away. "
                                         + "If you want to connect something, use the special UI for that.")
    public @interface Config {
        @AttributeDefinition(name = KEY_ACTIVE_CONNECTIONS,
                             description = "List of the active connections (e.g. endpoint:a-endpoint:b).",
                             required = false)
        String[] activeConnections() default {};

        @AttributeDefinition(name = KEY_AUTOCONNECT,
                             description = "When this is set to true, every new Endpoint will trigger an autoconnect call")
        boolean autoconnect() default false;
    }

    private final Map<String, Object> otherProperties;
    private final SortedMap<String, EndpointWrapper> endpointWrappers;
    private final MessageListenerContainer messageListenerContainer;

    private final Set<String> activeConnections;

    private boolean autoconnect;

    public ConnectionManagerImpl() {
        endpointWrappers = new TreeMap<String, EndpointWrapper>();
        otherProperties = new HashMap<String, Object>();
        messageListenerContainer = new MessageListenerContainer();

        activeConnections = new TreeSet<String>();
        autoconnect = false;
    }

    // This reference is only needed to make sure that the EndpointWrapper
    @SuppressWarnings("unused")
    private FlexiblePowerContext flexiblePowerContext;

    @Reference
    public void setFlexiblePowerContext(FlexiblePowerContext flexiblePowerContext) {
        this.flexiblePowerContext = flexiblePowerContext;
    }

    private ConfigurationAdmin configurationAdmin;

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    private Configuration configuration;

    @Activate
    public synchronized void activate() throws IOException {
        configuration = configurationAdmin.getConfiguration(getClass().getName());

        // Storing the configuration
        activeConnections.clear();
        otherProperties.clear();

        Dictionary<String, Object> properties = configuration.getProperties();
        if (properties != null) {
            for (Enumeration<String> keys = properties.keys(); keys.hasMoreElements();) {
                String key = keys.nextElement();
                otherProperties.put(key, properties.get(key));
            }

            Object activeConnections = properties.get(KEY_ACTIVE_CONNECTIONS);
            if (activeConnections != null) {
                if (activeConnections instanceof String) {
                    this.activeConnections.add(activeConnections.toString());
                } else if (activeConnections instanceof List) {
                    for (Object item : (List<?>) activeConnections) {
                        this.activeConnections.add(item.toString());
                    }
                } else if (activeConnections instanceof String[]) {
                    for (String item : (String[]) activeConnections) {
                        this.activeConnections.add(item);
                    }
                } else {
                    throw new IllegalArgumentException("The active connections should be a list of strings");
                }
            }

            parseAutoConnect(properties);
        }
        logger.debug("These connections are configured at boottime: {}", activeConnections);

        for (EndpointWrapper leftWrapper : endpointWrappers.values()) {
            for (EndpointPortImpl leftPort : leftWrapper.getPorts().values()) {
                for (PotentialConnectionImpl connection : leftPort.getPotentialConnections().values()) {
                    if (connection.isConnectable() && activeConnections.contains(connection.toString())) {
                        logger.info("Auto-starting connection on {}", connection);
                        connection.connect();
                    }
                }
            }
        }
    }

    private void parseAutoConnect(Dictionary<String, Object> properties) {
        Object autoconnect = properties.get(KEY_AUTOCONNECT);
        if (autoconnect != null) {
            if (autoconnect instanceof Boolean) {
                this.autoconnect = (Boolean) autoconnect;
            } else {
                this.autoconnect = Boolean.parseBoolean(autoconnect.toString());
            }
        }
    }

    @Modified
    public void modified(Map<String, Object> properties) {
        // Only the autoConnect change will be parsed, other changes will be ignored
        parseAutoConnect(new Hashtable<String, Object>(properties));
    }

    @Deactivate
    public synchronized void deactivate() {
        configuration = null;
        messageListenerContainer.close();
    }

    /**
     * Adds the key to the active connections and updates the configuration accordingly. This won't update when the
     * {@link #updateConnections()} method is running.
     *
     * @param key
     *            The key of the {@link PotentialConnection} that should be added
     */
    synchronized void connectedPort(String key) {
        if (activeConnections.add(key)) {
            storeConnections();
        }
    }

    /**
     * Removes the key from the active connections and updates the configuration accordingly. This won't update when the
     * {@link #updateConnections()} method is running.
     *
     * @param key
     *            The key of the {@link PotentialConnection} that should be added
     */
    synchronized void disconnectedPort(String key) {
        if (activeConnections.remove(key)) {
            storeConnections();
        }
    }

    private boolean waitWithStoring = false;

    /**
     * Stores the active connections in the configuration of this component
     */
    private void storeConnections() {
        if (!waitWithStoring) {
            // If the configuration is null, it means that the updates are done while the activate and deactivate
            // methods
            // are not active. This is probably due to the fact that it is booting up or is shutting down. Then we don't
            // need to update the configuration.
            if (configuration != null) {
                Dictionary<String, Object> properties = new Hashtable<String, Object>(otherProperties);
                if (!activeConnections.isEmpty()) {
                    properties.put(KEY_ACTIVE_CONNECTIONS, new ArrayList<String>(activeConnections));
                }

                if (!properties.isEmpty()) {
                    try {
                        configuration.update(properties);
                    } catch (IOException e) {
                        logger.warn("Could not store the new active connections: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Reference(name = "endpoint",
               service = Endpoint.class,
               cardinality = ReferenceCardinality.MULTIPLE,
               policy = ReferencePolicy.DYNAMIC)
    public synchronized void addEndpoint(Endpoint endpoint, Map<String, ?> properties) {
        try {
            String key = getKey(endpoint, properties);
            if (key != null) {
                EndpointWrapper wrapper = new EndpointWrapper(key, endpoint, this);
                endpointWrappers.put(key, wrapper);
                detectPossibleConnections(wrapper);
                logger.debug("Added endpoint on key [{}]", key);

                if (autoconnect) {
                    autoConnect();
                }
            }
        } catch (IllegalArgumentException ex) {
            logger.warn("Could not add endpoint: {}", ex.getMessage());
        }
    }

    public synchronized void removeEndpoint(Endpoint endpoint, Map<String, ?> properties) {
        String key = getKey(endpoint, properties);
        if (key != null) {
            EndpointWrapper endpointWrapper = endpointWrappers.remove(key);
            if (endpointWrapper != null && endpointWrapper.getEndpoint() == endpoint) {
                endpointWrapper.close();
                logger.debug("Removed endpoint on key [{}]", key);
            }
        }
    }

    private String getKey(Endpoint endpoint, Map<String, ?> properties) {
        String key = (String) properties.get(Constants.SERVICE_PID);
        if (key == null) {
            // TODO: what other way of persistent ID's can we have???
            key = endpoint.getClass().getName();
        }
        return key;
    }

    private void detectPossibleConnections(EndpointWrapper leftWrapper) {
        for (EndpointPortImpl left : leftWrapper.getPorts().values()) {
            for (EndpointWrapper rightWrapper : endpointWrappers.values()) {
                if (leftWrapper != rightWrapper) {
                    for (EndpointPortImpl right : rightWrapper.getPorts().values()) {
                        if (isSubset(left.getPort().sends(), right.getPort().accepts()) && isSubset(right.getPort()
                                                                                                         .sends(),
                                                                                                    left.getPort()
                                                                                                        .accepts())) {
                            PotentialConnectionImpl connection = new PotentialConnectionImpl(left, right);
                            logger.info("Found matching ports: {} <--> {}", left, right);
                            left.addMatch(connection);
                            right.addMatch(connection);

                            String key = connection.toString();
                            if (activeConnections.contains(key)) {
                                logger.info("Auto-starting connection on {}", connection);
                                connection.connect();
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isSubset(Class<?>[] sends, Class<?>[] accepts) {
        boolean correct = true;
        for (Class<?> send : sends) {
            correct = false;
            for (Class<?> accept : accepts) {
                if (accept.isAssignableFrom(send)) {
                    correct = true;
                    break;
                }
            }
            if (!correct) {
                break;
            }
        }
        return correct;
    }

    @Reference(name = "messageListener",
               service = MessageListener.class,
               cardinality = ReferenceCardinality.MULTIPLE,
               policy = ReferencePolicy.DYNAMIC)
    public synchronized void addMessageListener(MessageListener messageListener) {
        messageListenerContainer.addMessageListener(messageListener);
    }

    public synchronized void removeMessageListener(MessageListener messageListener) {
        messageListenerContainer.removeMessageListener(messageListener);
    }

    @Override
    public ManagedEndpoint getEndpoint(String pid) {
        EndpointWrapper wrapper = endpointWrappers.get(pid);
        if (wrapper == null) {
            SortedMap<String, EndpointWrapper> tailMap = endpointWrappers.tailMap(pid);
            if (!tailMap.isEmpty()) {
                wrapper = tailMap.get(tailMap.firstKey());
                if (!wrapper.getPid().startsWith(pid)) {
                    wrapper = null;
                }
            }
        }
        return wrapper;
    }

    @Override
    public SortedMap<String, EndpointWrapper> getEndpoints() {
        return Collections.unmodifiableSortedMap(endpointWrappers);
    }

    @Override
    public String toString() {
        return endpointWrappers.toString();
    }

    @Override
    public synchronized void autoConnect() {
        waitWithStoring = true;

        for (EndpointWrapper ew : endpointWrappers.values()) {
            for (EndpointPortImpl port : ew.getPorts().values()) {
                // Try each port detected in the system. We can only auto-connect ports that have single cardinality
                if (port.getCardinality() == Cardinality.SINGLE) {
                    SortedMap<String, PotentialConnectionImpl> potentialConnections = port.getPotentialConnections();
                    // If there is only 1 potential connection to be made, it can be connected
                    if (potentialConnections.size() == 1) {
                        PotentialConnectionImpl connection = potentialConnections.get(potentialConnections.firstKey());
                        synchronized (connection) {
                            // But only if it not connected already
                            if (!connection.isConnected()) {
                                EndpointPortImpl otherEnd = connection.getOtherEnd(port);
                                // Or if the other is has a single cardinality and has other potential connections that
                                // it can make
                                if ((otherEnd.getCardinality() == Cardinality.SINGLE
                                     && otherEnd.getPotentialConnections()
                                                .size() == 1)
                                    || otherEnd.getCardinality() == Cardinality.MULTIPLE) {
                                    connection.connect();
                                    logger.debug("Autoconnected [" + port + "] to [" + otherEnd + "]");
                                }
                            }
                        }
                    }
                }
            }
        }

        waitWithStoring = false;
        storeConnections();
    }

    public MessageListenerContainer getMessageListenerContainer() {
        return messageListenerContainer;
    }
}
