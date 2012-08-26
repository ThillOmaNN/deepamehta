package de.deepamehta.core.impl.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.DeepaMehtaTransaction;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginContext;
import de.deepamehta.core.service.CoreEvent;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.service.Listener;
import de.deepamehta.core.service.Plugin;
import de.deepamehta.core.service.PluginInfo;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.SecurityHandler;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;



public class PluginImpl implements Plugin, EventHandler {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String PLUGIN_DEFAULT_PACKAGE = "de.deepamehta.core.osgi";
    private static final String PLUGIN_CONFIG_FILE = "/plugin.properties";
    private static final String PLUGIN_ACTIVATED = "de/deepamehta/core/plugin_activated";   // topic of the OSGi event

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private PluginContext pluginContext;
    private BundleContext bundleContext;

    private Bundle      pluginBundle;
    private String      pluginUri;          // This bundle's symbolic name, e.g. "de.deepamehta.webclient"
    private String      pluginName;         // This bundle's name = POM project name, e.g. "DeepaMehta 4 Webclient"
    private String      pluginClass;

    private Properties  pluginProperties;   // Read from file "plugin.properties"
    private String      pluginPackage;
    private PluginInfo  pluginInfo;
    private Set<String> pluginDependencies; // plugin URIs as read from "importModels" property
    private Topic       pluginTopic;        // Represents this plugin in DB. Holds plugin migration number.

    // Consumed services
    private EmbeddedService dms;
    private WebPublishingService webPublishingService;
    private EventAdmin eventService;        // needed to post the PLUGIN_ACTIVATED OSGi event

    // Provided OSGi service
    private ServiceRegistration registration;

    // Provided resources
    private WebResources webResources;
    private WebResources directoryResource;
    private RestResource restResource;

    private List<ServiceTracker> coreServiceTrackers = new ArrayList();
    private List<ServiceTracker> pluginServiceTrackers = new ArrayList();

    private Logger logger = Logger.getLogger(getClass().getName());



    // ---------------------------------------------------------------------------------------------------- Constructors

    public PluginImpl(PluginContext pluginContext) {
        this.pluginContext = pluginContext;
        this.bundleContext = pluginContext.getBundleContext();
        //
        this.pluginBundle = bundleContext.getBundle();
        this.pluginUri = pluginBundle.getSymbolicName();
        this.pluginName = (String) pluginBundle.getHeaders().get("Bundle-Name");
        this.pluginClass = (String) pluginBundle.getHeaders().get("Bundle-Activator");
        //
        this.pluginProperties = readConfigFile();
        this.pluginPackage = getConfigProperty("pluginPackage", pluginContext.getClass().getPackage().getName());
        this.pluginInfo = new PluginInfoImpl(pluginUri, pluginBundle);
        this.pluginDependencies = getPluginDependencies();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public void start() {
        if (pluginDependencies.size() > 0) {
            registerEventListener();
        }
        //
        registerPluginService();
        //
        createCoreServiceTrackers();    // ### FIXME: move to constructor?
        createPluginServiceTrackers();  // ### FIXME: move to constructor?
        //
        openCoreServiceTrackers();
    }

    public void stop() {
        closeCoreServiceTrackers();
    }

    // ---

    public void publishDirectory(String directoryPath, String uriNamespace, SecurityHandler securityHandler) {
        try {
            logger.info("### Publishing directory \"" + directoryPath + "\" at URI namespace \"" + uriNamespace + "\"");
            //
            if (directoryResource != null) {
                throw new RuntimeException(this + " has already published a directory; " +
                    "only one per plugin is supported");
            }
            //
            directoryResource = webPublishingService.addWebResources(directoryPath, uriNamespace, securityHandler);
        } catch (Exception e) {
            throw new RuntimeException("Publishing directory \"" + directoryPath + "\" at URI namespace \"" +
                uriNamespace + "\" failed", e);
        }
    }

    // ---

    /**
     * Uses the plugin bundle's class loader to find a resource.
     *
     * @return  A InputStream object or null if no resource with this name is found.
     */
    public InputStream getResourceAsStream(String name) throws IOException {
        // We always use the plugin bundle's class loader to access the resource.
        // getClass().getResource() would fail for generic plugins (plugin bundles not containing a plugin
        // subclass) because the core bundle's class loader would be used and it has no access.
        URL url = pluginBundle.getResource(name);
        if (url != null) {
            return url.openStream();
        } else {
            return null;
        }
    }

    // ---

    @Override
    public String toString() {
        return "plugin \"" + pluginName + "\"";
    }



    // ************************************
    // *** Event Handler Implementation ***
    // ************************************



    @Override
    public void handleEvent(Event event) {
        String pluginUri = null;
        try {
            if (!event.getTopic().equals(PLUGIN_ACTIVATED)) {
                throw new RuntimeException("Unexpected event: " + event);
            }
            //
            pluginUri = (String) event.getProperty(EventConstants.BUNDLE_SYMBOLICNAME);
            if (!hasDependency(pluginUri)) {
                return;
            }
            //
            logger.info("Handling PLUGIN_ACTIVATED event from \"" + pluginUri + "\" for " + this);
            checkServiceAvailability();
        } catch (Exception e) {
            logger.severe("Handling PLUGIN_ACTIVATED event from \"" + pluginUri + "\" for " + this + " failed:");
            e.printStackTrace();
            // Note: we don't throw through the OSGi container here. It would not print out the stacktrace.
        }
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods

    String getUri() {
        return pluginUri;
    }

    PluginInfo getInfo() {
        return pluginInfo;
    }

    Topic getPluginTopic() {
        return pluginTopic;
    }

    // ---

    /**
     * Returns a plugin configuration property (as read from file "plugin.properties")
     * or <code>null</code> if no such property exists.
     */
    String getConfigProperty(String key) {
        return getConfigProperty(key, null);
    }

    String getConfigProperty(String key, String defaultValue) {
        return pluginProperties.getProperty(key, defaultValue);
    }

    // ---

    /**
     * Returns the migration class name for the given migration number.
     *
     * @return  the fully qualified migration class name, or <code>null</code> if the migration package is unknown.
     *          This is the case if the plugin bundle contains no Plugin subclass and the "pluginPackage" config
     *          property is not set.
     */
    String getMigrationClassName(int migrationNr) {
        if (pluginPackage.equals(PLUGIN_DEFAULT_PACKAGE)) {
            return null;    // migration package is unknown
        }
        //
        return pluginPackage + ".migrations.Migration" + migrationNr;
    }

    void setMigrationNr(int migrationNr) {
        pluginTopic.setChildTopicValue("dm4.core.plugin_migration_nr", new SimpleValue(migrationNr));
    }

    // ---

    /**
     * Uses the plugin bundle's class loader to load a class by name.
     *
     * @return  the class, or <code>null</code> if the class is not found.
     */
    Class loadClass(String className) {
        try {
            return pluginBundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === Config Properties ===

    private Properties readConfigFile() {
        try {
            Properties properties = new Properties();
            InputStream in = getResourceAsStream(PLUGIN_CONFIG_FILE);
            if (in != null) {
                logger.info("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this);
                properties.load(in);
            } else {
                logger.info("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this +
                    " ABORTED -- file does not exist");
            }
            return properties;
        } catch (Exception e) {
            throw new RuntimeException("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this + " failed", e);
        }
    }



    // === Service Tracking ===

    private void createCoreServiceTrackers() {
        coreServiceTrackers.add(createServiceTracker(DeepaMehtaService.class));
        coreServiceTrackers.add(createServiceTracker(WebPublishingService.class));
        coreServiceTrackers.add(createServiceTracker(EventAdmin.class));
    }

    private void createPluginServiceTrackers() {
        String consumedServiceInterfaces = getConfigProperty("consumedServiceInterfaces");
        if (consumedServiceInterfaces == null) {
            return;
        }
        //
        String[] serviceInterfaces = consumedServiceInterfaces.split(", *");
        for (int i = 0; i < serviceInterfaces.length; i++) {
            pluginServiceTrackers.add(createServiceTracker(serviceInterfaces[i]));
        }
    }

    // ---

    private ServiceTracker createServiceTracker(Class serviceInterface) {
        return createServiceTracker(serviceInterface.getName());
    }

    private ServiceTracker createServiceTracker(final String serviceInterface) {
        return new ServiceTracker(bundleContext, serviceInterface, null) {

            @Override
            public Object addingService(ServiceReference serviceRef) {
                Object service = null;
                try {
                    service = super.addingService(serviceRef);
                    addService(service, serviceInterface);
                } catch (Exception e) {
                    logger.severe("Adding service " + service + " to plugin \"" + pluginName + "\" failed:");
                    e.printStackTrace();
                    // Note: we don't throw through the OSGi container here. It would not print out the stacktrace.
                }
                return service;
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                try {
                    removeService(service, serviceInterface);
                    super.removedService(ref, service);
                } catch (Exception e) {
                    logger.severe("Removing service " + service + " from plugin \"" + pluginName + "\" failed:");
                    e.printStackTrace();
                    // Note: we don't throw through the OSGi container here. It would not print out the stacktrace.
                }
            }
        };
    }

    // ---

    private void openCoreServiceTrackers() {
        openServiceTrackers(coreServiceTrackers);
    }

    private void closeCoreServiceTrackers() {
        closeServiceTrackers(coreServiceTrackers);
    }

    private void openPluginServiceTrackers() {
        openServiceTrackers(pluginServiceTrackers);
    }

    private void closePluginServiceTrackers() {
        closeServiceTrackers(pluginServiceTrackers);
    }

    // ---

    private void openServiceTrackers(List<ServiceTracker> serviceTrackers) {
        for (ServiceTracker serviceTracker : serviceTrackers) {
            serviceTracker.open();
        }
    }

    private void closeServiceTrackers(List<ServiceTracker> serviceTrackers) {
        // Note: we close the service trackers in reverse creation order. Consider this case: when a consumed plugin
        // service goes away the core service is still needed to deliver the PLUGIN_SERVICE_GONE event. ### STILL TRUE?
        /* ListIterator<ServiceTracker> i = serviceTrackers.listIterator(serviceTrackers.size());
        while (i.hasPrevious()) {
            i.previous().close();
        } */
        for (ServiceTracker serviceTracker : serviceTrackers) {
            serviceTracker.close();
        }
    }

    // ---

    private void addService(Object service, String serviceInterface) {
        if (service instanceof DeepaMehtaService) {
            logger.info("Adding DeepaMehta 4 core service to " + this);
            setCoreService((EmbeddedService) service);
            openPluginServiceTrackers();
            // Note: registering the listeners is deferred until the plugin is installed and the
            // CoreEvent.POST_INSTALL_PLUGIN is delivered. See activate() below.
            // Consider the Access Control plugin: it can't set a topic's creator before the "admin" user is created.
            checkServiceAvailability();
        } else if (service instanceof WebPublishingService) {
            logger.info("Adding Web Publishing service to " + this);
            webPublishingService = (WebPublishingService) service;
            registerWebResources();
            registerRestResources();
            checkServiceAvailability();
        } else if (service instanceof EventAdmin) {
            logger.info("Adding Event Admin service to " + this);
            eventService = (EventAdmin) service;
            checkServiceAvailability();
        } else if (service instanceof PluginService) {
            logger.info("Adding \"" + serviceInterface + "\" to " + this);
            deliverEvent(CoreEvent.PLUGIN_SERVICE_ARRIVED, (PluginService) service);
        }
    }

    private void removeService(Object service, String serviceInterface) {
        if (service == dms) {
            logger.info("Removing DeepaMehta 4 core service from " + this);
            closePluginServiceTrackers();   // core service is needed to deliver the PLUGIN_SERVICE_GONE events
            unregisterListeners();
            unregisterPlugin();
            setCoreService(null);
        } else if (service == webPublishingService) {
            logger.info("Removing Web Publishing service from " + this);
            unregisterRestResources();
            unregisterWebResources();
            unregisterDirectoryResource();
            webPublishingService = null;
        } else if (service == eventService) {
            logger.info("Removing Event Admin service from " + this);
            eventService = null;
        } else if (service instanceof PluginService) {
            logger.info("Removing plugin service \"" + serviceInterface + "\" from " + this);
            deliverEvent(CoreEvent.PLUGIN_SERVICE_GONE, (PluginService) service);
        }
    }

    // ---

    private void setCoreService(EmbeddedService dms) {
        this.dms = dms;
        pluginContext.setCoreService(dms);
    }

    // ---

    /**
     * Checks if the requirements are met, and if so, activates this plugin.
     *
     * The requirements:
     *   - the 3 core services are available (DeepaMehtaService, WebPublishingService, EventAdmin).
     *   - the plugin dependencies (according to this plugin's "importModels" property) are active.
     *
     * After activation:
     *   - posts the PLUGIN_ACTIVATED OSGi event.
     *   - checks if all plugins are active, and if so, fires the {@link CoreEvent.ALL_PLUGINS_ACTIVE} event.
     */
    private void checkServiceAvailability() {
        // Note: The Web Publishing service is not strictly required for activation, but we must ensure
        // ALL_PLUGINS_ACTIVE is not fired before the Web Publishing service becomes available.
        if (dms == null || webPublishingService == null || eventService == null || !dependenciesAvailable()) {
            return;
        }
        //
        if (activate()) {
            postPluginActivatedEvent();
            if (dms.pluginManager.checkAllPluginsActive()) {
                logger.info("########## All Plugins Active ##########");
                dms.fireEvent(CoreEvent.ALL_PLUGINS_ACTIVE);
            }
        }
    }



    // === Activation ===

    /**
     * Activates this plugin. This comprises:
     * - install the plugin in the database
     * - register the plugin at the DeepaMehta core service
     *
     * If this plugin is already activated, nothing is performed and false is returned.
     * Otherwise true is returned.
     *
     * Acivation relies on both, the DeepaMehtaService and the EventAdmin service.
     * This method is called once both services become available. ### FIXDOC
     */
    private synchronized boolean activate() {
        try {
            // Note: we must not activate a plugin twice.
            // This would happen e.g. if a dependency plugin is redeployed.
            if (isRegistered()) {
                logger.info("Activation of " + this + " ABORTED -- already activated");
                return false;
            }
            //
            logger.info("----- Activating " + this + " -----");
            installPluginInDB();        // relies on DeepaMehtaService
            initializePlugin();         // relies on DeepaMehtaService
            registerListeners();        // relies on DeepaMehtaService
            registerPlugin();           // relies on DeepaMehtaService (and committed migrations)
            logger.info("----- Activation of " + this + " complete -----");
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Activation of " + this + " failed", e);
        }
    }



    // === Installation ===

    /**
     * Installs the plugin in the database. This comprises:
     * - create topic of type "Plugin"
     * - run migrations
     * - fires the {@link CoreEvent.POST_INSTALL_PLUGIN} event
     * - fires the {@link CoreEvent.INTRODUCE_TOPIC_TYPE} event (multiple times)
     */
    private void installPluginInDB() {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            boolean isCleanInstall;
            // 1) create "Plugin" topic
            pluginTopic = fetchPluginTopic();
            if (pluginTopic != null) {
                logger.info("Installing " + this + " in the database ABORTED -- already installed");
                isCleanInstall = false;
            } else {
                logger.info("Installing " + this + " in the database");
                createPluginTopic();
                isCleanInstall = true;
            }
            // 2) run migrations
            dms.migrationManager.runPluginMigrations(this, isCleanInstall);
            // 3) post install
            if (isCleanInstall) {
                deliverEvent(CoreEvent.POST_INSTALL_PLUGIN);
                introduceTypesToPlugin();
            }
            //
            tx.success();
        } catch (Exception e) {
            logger.warning("ROLLBACK! (" + this + ")");
            throw new RuntimeException("Installing " + this + " in the database failed", e);
        } finally {
            tx.finish();
        }
    }

    /**
     * Creates a Plugin topic in the DB.
     * <p>
     * A Plugin topic represents an installed plugin and is used to track its version.
     */
    private void createPluginTopic() {
        pluginTopic = dms.createTopic(new TopicModel(pluginUri, "dm4.core.plugin", new CompositeValue()
            .put("dm4.core.plugin_name", pluginName)
            .put("dm4.core.plugin_symbolic_name", pluginUri)
            .put("dm4.core.plugin_migration_nr", 0)
        ), null);   // FIXME: clientState=null
    }

    private Topic fetchPluginTopic() {
        return dms.getTopic("uri", new SimpleValue(pluginUri), false, null);        // fetchComposite=false
    }

    private void introduceTypesToPlugin() {
        for (String topicTypeUri : dms.getTopicTypeUris()) {
            try {
                TopicType topicType = dms.getTopicType(topicTypeUri, null);     // clientState=null
                deliverEvent(CoreEvent.INTRODUCE_TOPIC_TYPE, topicType, null);  // clientState=null
            } catch (Exception e) {
                throw new RuntimeException("Introducing topic type \"" + topicTypeUri + "\" to " + this + " failed", e);
            }
        }
    }



    // === Initialization ===

    private void initializePlugin() {
        deliverEvent(CoreEvent.INITIALIZE_PLUGIN);
    }



    // === Core Registration ===

    /**
     * Registers this plugin at the DeepaMehta core service.
     */
    private void registerPlugin() {
        logger.info("Registering " + this + " at DeepaMehta 4 core service");
        dms.pluginManager.registerPlugin(this);
    }

    private void unregisterPlugin() {
        logger.info("Unregistering " + this + " at DeepaMehta 4 core service");
        dms.pluginManager.unregisterPlugin(pluginUri);
    }

    // ---

    /**
     * Returns true if this plugin is registered at the DeepaMehta core service.
     */
    private boolean isRegistered() {
        return isRegistered(pluginUri);
    }

    private boolean isRegistered(String pluginUri) {
        return dms.pluginManager.isPluginRegistered(pluginUri);
    }



    // === Events ===

    private void registerListeners() {
        List<CoreEvent> events = getEvents();
        //
        if (events.size() == 0) {
            logger.info("Registering listeners of " + this + " at DeepaMehta 4 core service ABORTED " +
                "-- no listeners implemented");
            return;
        }
        //
        logger.info("Registering " + events.size() + " listeners of " + this + " at DeepaMehta 4 core service");
        for (CoreEvent event : events) {
            dms.listenerRegistry.addListener(event, (Listener) pluginContext);
        }
    }

    private void unregisterListeners() {
        List<CoreEvent> events = getEvents();
        if (events.size() == 0) {
            return;
        }
        //
        logger.info("Unregistering listeners of " + this + " at DeepaMehta 4 core service");
        for (CoreEvent event : events) {
            dms.listenerRegistry.removeListener(event, (Listener) pluginContext);
        }
    }

    // ---

    /**
     * Returns the events this plugin is listening to.
     */
    private List<CoreEvent> getEvents() {
        List<CoreEvent> events = new ArrayList();
        for (Class interfaze : pluginContext.getClass().getInterfaces()) {
            if (isListenerInterface(interfaze)) {
                CoreEvent event = CoreEvent.fromListenerInterface(interfaze);
                logger.fine("### Listener Interface: " + interfaze + ", event=" + event);
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Delivers an event to this plugin, provided this plugin is a listener for that event.
     * <p>
     * By this method this plugin delivers an "internal" event to itself. An internal event is bound
     * to a particular plugin, in contrast to being fired and delivered to all registered plugins.
     * <p>
     * There are 5 internal events:
     *   - POST_INSTALL_PLUGIN
     *   - INTRODUCE_TOPIC_TYPE (has a double nature)
     *   - INITIALIZE_PLUGIN
     *   - PLUGIN_SERVICE_ARRIVED
     *   - PLUGIN_SERVICE_GONE
     */
    private Object deliverEvent(CoreEvent event, Object... params) {
        if (!isListener(event)) {
            return null;
        }
        //
        logger.fine("### Delivering internal plugin event " + event + " from/to " + this);
        return dms.listenerRegistry.deliverEvent((Listener) pluginContext, event, params);
    }

    // ---

    /**
     * Returns true if the specified interface is a listener interface.
     * A listener interface is a sub-interface of {@link Listener}.
     */
    private boolean isListenerInterface(Class interfaze) {
        return Listener.class.isAssignableFrom(interfaze);
    }

    /**
     * Returns true if this plugin is a listener for the specified event.
     */
    private boolean isListener(CoreEvent event) {
        return event.listenerInterface.isAssignableFrom(pluginContext.getClass());
    }



    // === Plugin Service ===

    /**
     * Registers this plugin's OSGi service at the OSGi framework.
     * If the plugin doesn't provide an OSGi service nothing is performed.
     */
    private void registerPluginService() {
        String serviceInterface = null;
        try {
            serviceInterface = getConfigProperty("providedServiceInterface");
            if (serviceInterface == null) {
                logger.info("Registering OSGi service of " + this + " ABORTED -- no OSGi service provided");
                return;
            }
            //
            logger.info("Registering service \"" + serviceInterface + "\" at OSGi framework");
            registration = bundleContext.registerService(serviceInterface, pluginContext, null);
        } catch (Exception e) {
            throw new RuntimeException("Registering service of " + this + " at OSGi framework failed " +
                "(serviceInterface=\"" + serviceInterface + "\")", e);
        }
    }

    /* ### FIXME: needed?
    private void unregisterPluginService() {
        String serviceInterface = null;
        try {
            serviceInterface = getConfigProperty("providedServiceInterface");
            if (serviceInterface == null) {
                return;
            }
            //
            logger.info("Unregistering service \"" + serviceInterface + "\" at OSGi framework");
            registration.unregister();
        } catch (Exception e) {
            throw new RuntimeException("Unregistering service of " + this + " at OSGi framework failed " +
                "(serviceInterface=\"" + serviceInterface + "\")", e);
        }
    } */



    // === Web Resources ===

    /**
     * Registers this plugin's web resources at the Web Publishing service.
     * If the plugin doesn't provide web resources nothing is performed.
     */
    private void registerWebResources() {
        String uriNamespace = null;
        try {
            uriNamespace = getWebResourcesNamespace();
            if (uriNamespace == null) {
                logger.info("Registering Web resources of " + this + " ABORTED -- no Web resources provided");
                return;
            }
            //
            logger.info("Registering Web resources of " + this + " at URI namespace \"" + uriNamespace + "\"");
            webResources = webPublishingService.addWebResources(pluginBundle, uriNamespace);
        } catch (Exception e) {
            throw new RuntimeException("Registering Web resources of " + this + " failed " +
                "(uriNamespace=\"" + uriNamespace + "\")", e);
        }
    }

    private void unregisterWebResources() {
        if (webResources != null) {
            logger.info("Unregistering Web resources of " + this);
            webPublishingService.removeWebResources(webResources);
        }
    }

    // ---

    private String getWebResourcesNamespace() {
        return pluginBundle.getEntry("/web") != null ? "/" + pluginUri : null;
    }



    // === Directory Resources ===

    // Note: registration is performed by public method publishDirectory()

    private void unregisterDirectoryResource() {
        if (directoryResource != null) {
            logger.info("Unregistering Directory resource of " + this);
            webPublishingService.removeWebResources(directoryResource);
        }
    }



    // === REST Resources ===

    /**
     * Registers this plugin's REST resources at the Web Publishing service.
     * If the plugin doesn't provide REST resources nothing is performed.
     */
    private void registerRestResources() {
        String uriNamespace = null;
        try {
            uriNamespace = webPublishingService.getUriNamespace(pluginContext);
            if (uriNamespace == null) {
                logger.info("Registering REST resources of " + this + " ABORTED -- no REST resources provided");
                return;
            }
            //
            logger.info("Registering REST resources of " + this + " at URI namespace \"" + uriNamespace + "\"");
            restResource = webPublishingService.addRestResource(pluginContext, getProviderClasses());
        } catch (Exception e) {
            unregisterWebResources();
            throw new RuntimeException("Registering REST resources of " + this + " failed " +
                "(uriNamespace=\"" + uriNamespace + "\")", e);
        }
    }

    private void unregisterRestResources() {
        if (restResource != null) {
            logger.info("Unregistering REST resources of " + this);
            webPublishingService.removeRestResource(restResource);
        }
    }

    // ---

    private Set<Class<?>> getProviderClasses() throws IOException {
        Set<Class<?>> providerClasses = new HashSet();
        String providerPackage = ("/" + pluginPackage + ".provider").replace('.', '/');
        Enumeration<String> e = pluginBundle.getEntryPaths(providerPackage);
        logger.fine("### Scanning package " + pluginPackage + ".provider");
        if (e != null) {
            while (e.hasMoreElements()) {
                String entryPath = e.nextElement();
                entryPath = entryPath.substring(0, entryPath.length() - 6);     // cut ".class"
                String className = entryPath.replace('/', '.');
                logger.fine("  # Found provider class: " + className);
                Class providerClass = loadClass(className);
                if (providerClass == null) {
                    throw new RuntimeException("Loading provider class \"" + className + "\" failed");
                }
                providerClasses.add(providerClass);
            }
        }
        //
        if (providerClasses.size() == 0) {
            logger.info("Registering provider classes of " + this + " ABORTED -- no provider classes provided");
        } else {
            logger.info("Registering " + providerClasses.size() + " provider classes of " + this);
        }
        //
        return providerClasses;
    }



    // === Plugin Dependencies ===

    private Set<String> getPluginDependencies() {
        Set<String> pluginDependencies = new HashSet();
        String importModels = getConfigProperty("importModels");
        if (importModels != null) {
            String[] pluginUris = importModels.split(", *");
            for (int i = 0; i < pluginUris.length; i++) {
                pluginDependencies.add(pluginUris[i]);
            }
        }
        return pluginDependencies;
    }

    private boolean hasDependency(String pluginUri) {
        return pluginDependencies.contains(pluginUri);
    }

    private boolean dependenciesAvailable() {
        for (String pluginUri : pluginDependencies) {
            if (!isRegistered(pluginUri)) {
                return false;
            }
        }
        return true;
    }

    private void registerEventListener() {
        String[] topics = new String[] {PLUGIN_ACTIVATED};
        Hashtable properties = new Hashtable();
        properties.put(EventConstants.EVENT_TOPIC, topics);
        bundleContext.registerService(EventHandler.class.getName(), this, properties);
    }

    private void postPluginActivatedEvent() {
        Properties properties = new Properties();
        properties.put(EventConstants.BUNDLE_SYMBOLICNAME, pluginUri);
        eventService.postEvent(new Event(PLUGIN_ACTIVATED, properties));
    }
}
