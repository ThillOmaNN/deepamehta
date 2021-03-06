function Association(assoc) {
    this.id       = assoc.id
    this.type_uri = assoc.type_uri
    this.role_1   = assoc.role_1
    this.role_2   = assoc.role_2
}

// === "Page Displayable" implementation ===

Association.prototype.get_type = function() {
    return dm4c.get_association_type(this.type_uri)
}

Association.prototype.get_commands = function(context) {
    return dm4c.get_association_commands(this, context)
}

// === Public API ===

Association.prototype.get_topic_1 = function() {
    return dm4c.fetch_topic(this.role_1.topic_id, false)    // fetch_composite=false
}

Association.prototype.get_topic_2 = function() {
    return dm4c.fetch_topic(this.role_2.topic_id, false)    // fetch_composite=false
}

// ---

Association.prototype.get_role_type_1 = function() {
    return dm4c.restc.get_topic_by_value("uri", this.role_1.role_type_uri)
}

Association.prototype.get_role_type_2 = function() {
    return dm4c.restc.get_topic_by_value("uri", this.role_2.role_type_uri)
}
