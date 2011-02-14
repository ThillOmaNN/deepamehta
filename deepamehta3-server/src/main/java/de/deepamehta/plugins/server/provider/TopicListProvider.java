package de.deepamehta.plugins.server.provider;

import de.deepamehta.core.model.Topic;
import de.deepamehta.core.util.JSONHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;



@Provider
public class TopicListProvider implements MessageBodyWriter<List<Topic>> {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** MessageBodyWriter Implementation ***
    // ****************************************



    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            logger.info("########## generic type=" + pt + "\n" +
                        "      ########## raw type=" + pt.getRawType() + "\n" +
                        "      ########## owner type=" + pt.getOwnerType() + "\n" +
                        "      ########## number of type args=" + typeArgs.length + "\n" +
                        "      ########## type arg[0]=" + typeArgs[0]);
            if (pt.getRawType() == List.class && typeArgs.length == 1 && typeArgs[0] == Topic.class) {
                // Note: unlike equals() isCompatible() ignores parameters
                // like "charset" in "application/json;charset=UTF-8"
                if (mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
                    logger.info("########## => TopicListProvider feels responsible!!!");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public long getSize(List<Topic> topics, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Topic> topics, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                        throws IOException, WebApplicationException {
        try {
            // logger.info("Writing " + entity + " to response stream");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(entityStream));
            JSONHelper.topicsToJson(topics).write(writer);
            writer.flush();
        } catch (Exception e) {
            throw new IOException("Writing " + topics + " to response stream failed", e);
        }
    }
}