package de.deepamehta.core.model;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * @author <a href="mailto:jri@deepamehta.de">Jörg Richter</a>
 */
public class TopicData {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    protected String uri;
    protected TopicValue value;
    protected String typeUri;
    protected Composite composite;

    // ---------------------------------------------------------------------------------------------------- Constructors

    /**
     * @param   uri     If <code>null</code> the topic will have no URI. This is OK.
     * @param   value   If <code>null</code> the topic will have no value. This is OK.
     */
    public TopicData(String uri, TopicValue value, String typeUri, Composite composite) {
        this.uri = uri;
        this.value = value;
        this.typeUri = typeUri;
        this.composite = composite;
    }

    public TopicData(TopicData topicData) {
        this(topicData.uri, topicData.value, topicData.typeUri, topicData.composite);
    }

    public TopicData(JSONObject topicData) {
        try {
            this.uri = topicData.getString("uri");
            this.value = new TopicValue(topicData.get("value"));
            this.typeUri = topicData.getString("topic_type");
        } catch (Exception e) {
            throw new RuntimeException("Parsing " + this + " failed", e);
        }
    }

    // ---

    protected TopicData() {
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public String getUri() {
        return uri;
    }

    public TopicValue getValue() {
        return value;
    }

    public String getTypeUri() {
        return typeUri;
    }

    public Composite getComposite() {
        return composite;
    }

    // ---

    public JSONObject toJSON() {
        try {
            JSONObject o = new JSONObject();
            o.put("uri", uri);
            o.put("value", value.value());
            o.put("topic_type", typeUri);
            o.put("composite", composite);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException("Serialization failed (" + this + ")", e);
        }
    }

    // ---

    @Override
    public String toString() {
        return "topic data (uri=\"" + uri + "\", value=" + value + ", typeUri=\"" + typeUri +
            "\", composite=" + composite + ")";
    }
}
