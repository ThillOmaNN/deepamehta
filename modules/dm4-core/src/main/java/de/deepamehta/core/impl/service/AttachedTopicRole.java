package de.deepamehta.core.impl.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.TopicRole;
import de.deepamehta.core.model.TopicRoleModel;

import java.util.logging.Logger;



/**
 * A topic role that is attached to the {@link DeepaMehtaService}.
 */
class AttachedTopicRole extends AttachedRole implements TopicRole {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());
    
    // ---------------------------------------------------------------------------------------------------- Constructors

    AttachedTopicRole(TopicRoleModel model, Association assoc, EmbeddedService dms) {
        super(model, assoc, dms);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // === TopicRole Implementation ===

    @Override
    public long getTopicId() {
        return getModel().getTopicId();
    }

    @Override
    public String getTopicUri() {
        return getModel().getTopicUri();
    }

    @Override
    public boolean topicIdentifiedByUri() {
        return getModel().topicIdentifiedByUri();
    }



    // === Role Overrides ===

    @Override
    public void setRoleTypeUri(String roleTypeUri) {
        // 1) update memory
        super.setRoleTypeUri(roleTypeUri);
        // 2) update DB
        storeRoleTypeUri(roleTypeUri);
    }

    // === AttachedRole Overrides ===

    @Override
    public TopicRoleModel getModel() {
        return (TopicRoleModel) super.getModel();
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === Store ===

    private void storeRoleTypeUri(String roleTypeUri) {
        dms.storage.setRoleTypeUri(getAssociation().getId(), getTopicId(), roleTypeUri);
    }    
}
