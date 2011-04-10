package de.deepamehta.core.storage.impl;

import de.deepamehta.core.model.Association;
import de.deepamehta.core.model.AssociationData;
import de.deepamehta.core.model.AssociationDefinition;
import de.deepamehta.core.model.Role;
import de.deepamehta.core.model.Topic;
import de.deepamehta.core.model.TopicData;
import de.deepamehta.core.model.TopicValue;
import de.deepamehta.core.storage.DeepaMehtaStorage;
import de.deepamehta.core.storage.DeepaMehtaTransaction;

import de.deepamehta.hypergraph.ConnectedHyperNode;
import de.deepamehta.hypergraph.HyperEdge;
import de.deepamehta.hypergraph.HyperGraph;
import de.deepamehta.hypergraph.HyperNode;
import de.deepamehta.hypergraph.HyperNodeRole;
import de.deepamehta.hypergraph.IndexMode;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;



/**
 * A bridge between the DeepaMehta storage abstraction and a HyperGraph implementation.
 */
public class HGStorageBridge implements DeepaMehtaStorage {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private HyperGraph hg;

    private final Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    public HGStorageBridge(HyperGraph hg) {
        this.hg = hg;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    // === Topics ===

    @Override
    public Topic getTopic(long topicId) {
        return buildTopic(hg.getHyperNode(topicId));
    }

    @Override
    public Topic getTopic(String key, TopicValue value) {
        HyperNode node = hg.getHyperNode(key, value.value());
        return node != null ? buildTopic(node) : null;
    }

    @Override
    public Topic getRelatedTopic(long topicId, String assocTypeUri, String myRoleTypeUri, String othersRoleTypeUri) {
        // FIXME: assocTypeUri not respected
        ConnectedHyperNode node = hg.getHyperNode(topicId).getConnectedHyperNode(myRoleTypeUri, othersRoleTypeUri);
        return node != null ? buildTopic(node.getHyperNode()) : null;
    }

    @Override
    public Set<Topic> getRelatedTopics(long topicId, String assocTypeUri, String myRoleTypeUri,
                                                                          String othersRoleTypeUri) {
        // FIXME: assocTypeUri not respected
        Set<ConnectedHyperNode> nodes = hg.getHyperNode(topicId).getConnectedHyperNodes(myRoleTypeUri,
                                                                                        othersRoleTypeUri);
        Set<Topic> topics = new HashSet();
        for (ConnectedHyperNode node : nodes) {
            topics.add(buildTopic(node.getHyperNode()));
        }
        return topics;
    }

    @Override
    public Set<Association> getAssociations(long topicId, String myRoleType) {
        Set<Association> assocs = new HashSet();
        HyperNode node = hg.getHyperNode(topicId);
        for (HyperEdge edge : node.getHyperEdges(myRoleType)) {
            Set<Role> roles = new HashSet();
            for (HyperNodeRole role : edge.getHyperNodes()) {
                roles.add(new Role(role.getHyperNode().getId(), role.getRoleType()));
            }
            assocs.add(buildAssociation(edge, null, roles));    // FIXME: typeUri=null
        }
        return assocs;
    }

    @Override
    public void setTopicValue(long topicId, TopicValue value) {
        hg.getHyperNode(topicId).setAttribute("value", value.value());
    }

    @Override
    public Topic createTopic(TopicData topicData) {
        checkUniqueness(topicData.getUri());
        // 1) create node
        HyperNode node = hg.createHyperNode();
        node.setAttribute("uri", topicData.getUri(), IndexMode.KEY);
        node.setAttribute("value", topicData.getValue().value());
        // 2) associate with type
        HyperNode topicType = lookupTopicType(topicData.getTypeUri());
        HyperEdge edge = hg.createHyperEdge();
        edge.addHyperNode(topicType, "dm3.core.type");
        edge.addHyperNode(node, "dm3.core.instance");
        //
        return buildTopic(node);
    }

    // === Associations ===

    @Override
    public Association createAssociation(AssociationData assocData) {
        HyperEdge edge = hg.createHyperEdge();          // FIXME: use association type
        for (Role role : assocData.getRoles()) {
            addAssociationRole(edge, role);
        }
        return buildAssociation(edge, assocData.getTypeUri(), assocData.getRoles());
    }

    @Override
    public void addAssociationRole(long assocId, Role role) {
        addAssociationRole(hg.getHyperEdge(assocId), role);
    }

    // === DB ===

    @Override
    public DeepaMehtaTransaction beginTx() {
        return new HGTransactionAdapter(hg);
    }

    @Override
    public boolean init() {
        // init core migration number
        boolean isCleanInstall = false;
        if (!hg.getHyperNode(0).hasAttribute("core_migration_nr")) {
            logger.info("Starting with a fresh DB -- Setting migration number to 0");
            setMigrationNr(0);
            setupMetaTypeNode();
            isCleanInstall = true;
        }
        return isCleanInstall;
    }

    @Override
    public void shutdown() {
        hg.shutdown();
    }

    @Override
    public int getMigrationNr() {
        return hg.getHyperNode(0).getInteger("core_migration_nr");
    }

    @Override
    public void setMigrationNr(int migrationNr) {
        hg.getHyperNode(0).setAttribute("core_migration_nr", migrationNr);
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private Topic buildTopic(HyperNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Tried to build a Topic from a null HyperNode");
        }
        //
        return new HGTopic(node.getId(), node.getString("uri"), new TopicValue(node.get("value")),
            getTopicTypeUri(node), null);   // composite=null
    }

    private Association buildAssociation(HyperEdge edge, String typeUri, Set<Role> roles) {
        if (edge == null) {
            throw new IllegalArgumentException("Tried to build an Association from a null HyperEdge");
        }
        //
        return new HGAssociation(edge.getId(), typeUri, roles);
    }

    // ---

    private HyperNode lookupTopic(String uri) {
        HyperNode topic = lookupHyperNode(uri);
        if (topic == null) {
            throw new RuntimeException("Topic \"" + uri + "\" not found");
        }
        return topic;
    }

    private HyperNode lookupTopicType(String topicTypeUri) {
        HyperNode topicType = lookupHyperNode(topicTypeUri);
        if (topicType == null) {
            throw new RuntimeException("Topic type \"" + topicTypeUri + "\" not found");
        }
        return topicType;
    }

    private HyperNode lookupRoleType(String roleTypeUri) {
        HyperNode roleType = lookupHyperNode(roleTypeUri);
        if (roleType == null) {
            throw new RuntimeException("Role type \"" + roleTypeUri + "\" not found");
        }
        return roleType;
    }

    // ---

    /**
     * Throws an exception if there is a topic with the given URI in the database.
     * If an empty string is given no check is performed.
     *
     * @param   uri     The URI to check. Must not be null.
     */
    private void checkUniqueness(String uri) {
        if (!uri.equals("") && lookupHyperNode(uri) != null) {
            throw new RuntimeException("Topic with URI \"" + uri + "\" exists already");
        }
    }

    private HyperNode lookupHyperNode(String uri) {
        return hg.getHyperNode("uri", uri);
    }

    // ---

    /**
     * Determines the topic type of a hyper node.
     *
     * @return  The topic type's URI.
     */
    private String getTopicTypeUri(HyperNode node) {
        return getTopicType(node).getString("uri");
    }

    /**
     * Determines the topic type of a hyper node.
     *
     * @return  The hyper node that represents the topic type.
     */
    private HyperNode getTopicType(HyperNode node) {
        ConnectedHyperNode typeNode = node.getConnectedHyperNode("dm3.core.instance", "dm3.core.type");
        if (typeNode == null) {
            throw new RuntimeException("Determining topic type failed (" + node + ")");
        }
        return typeNode.getHyperNode();
    }

    // ---

    private void addAssociationRole(HyperEdge edge, Role role) {
        String roleTypeUri = role.getRoleTypeUri();
        lookupRoleType(roleTypeUri);    // consistency check
        edge.addHyperNode(getRoleNode(role), roleTypeUri);
    }

    private HyperNode getRoleNode(Role role) {
        if (role.topicIdentifiedById()) {
            return hg.getHyperNode(role.getTopicId());
        } else {
            return lookupTopic(role.getTopicUri());
        }
    }

    // ---

    private void setupMetaTypeNode() {
        HyperNode refNode = hg.getHyperNode(0);
        refNode.setAttribute("uri", "dm3.core.meta_type", IndexMode.KEY);
        refNode.setAttribute("value", "Meta Type");
    }
}
