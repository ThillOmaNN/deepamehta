package de.deepamehta.plugins.accesscontrol.resources;

import de.deepamehta.plugins.accesscontrol.AccessControlPlugin;
import de.deepamehta.plugins.accesscontrol.model.Permissions;
import de.deepamehta.plugins.accesscontrol.model.Role;

import de.deepamehta.core.model.ClientContext;
import de.deepamehta.core.model.Topic;
import de.deepamehta.core.model.Relation;
import de.deepamehta.core.osgi.Activator;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.util.JSONHelper;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Cookie;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



@Path("/")
@Consumes("application/json")
@Produces("application/json")
public class AccessControlResource {

    private CoreService dms = Activator.getService();
    private AccessControlPlugin accessControl = (AccessControlPlugin) dms.getPlugin("de.deepamehta.3-accesscontrol");

    private Logger logger = Logger.getLogger(getClass().getName());

    @GET
    @Path("/user")
    public Topic getUser(@HeaderParam("Cookie") ClientContext clientContext) {
        logger.info("Cookie: " + clientContext);
        return accessControl.getUser(clientContext);
    }

    @GET
    @Path("/owner/{userId}/{typeUri}")
    public Topic getTopicByOwner(@PathParam("userId") long userId, @PathParam("typeUri") String typeUri) {
        return accessControl.getTopicByOwner(userId, typeUri);
    }

    @POST
    @Path("/topic/{topicId}/owner/{userId}")
    public void setOwner(@PathParam("topicId") long topicId, @PathParam("userId") long userId) {
        accessControl.setOwner(topicId, userId);
    }

    @POST
    @Path("/topic/{topicId}/role/{role}")
    public void createACLEntry(@PathParam("topicId") long topicId,
                               @PathParam("role") Role role, Permissions permissions) {
        logger.info("topicId=" + topicId + ", role=" + role + ", permissions=" + permissions);
        accessControl.createACLEntry(topicId, role, permissions);
    }

    @POST
    @Path("/user/{userId}/{workspaceId}")
    public void joinWorkspace(@PathParam("userId") long userId, @PathParam("workspaceId") long workspaceId) {
        accessControl.joinWorkspace(workspaceId, userId);
    }
}