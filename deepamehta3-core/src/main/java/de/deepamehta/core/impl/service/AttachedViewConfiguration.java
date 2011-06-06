package de.deepamehta.core.impl.service;

import de.deepamehta.core.Topic;
import de.deepamehta.core.ViewConfiguration;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.model.ViewConfigurationModel;



/**
 * A view configuration that is attached to the {@link DeepaMehtaService}.
 */
class AttachedViewConfiguration implements ViewConfiguration {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private final RoleModel configurable;
    private final ViewConfigurationModel model;
    private final EmbeddedService dms;

    // ---------------------------------------------------------------------------------------------------- Constructors

    AttachedViewConfiguration(RoleModel configurable, ViewConfigurationModel model, EmbeddedService dms) {
        this.configurable = configurable;
        this.model = model;
        this.dms = dms;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public Iterable<TopicModel> getConfigTopics() {
        return model.getConfigTopics();
    }

    @Override
    public void addConfigTopic(TopicModel configTopic) {
        // update memory
        model.addConfigTopic(configTopic);
        // update DB
        storeConfigTopic(configTopic);
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    void store() {
        for (TopicModel configTopic : getConfigTopics()) {
            storeConfigTopic(configTopic);
        }
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    private void storeConfigTopic(TopicModel configTopic) {
        Topic topic = dms.createTopic(configTopic, null);   // FIXME: clientContext=null
        dms.createAssociation("dm3.core.association", configurable,
            new TopicRoleModel(topic.getId(), "dm3.core.view_config"));
    }
}