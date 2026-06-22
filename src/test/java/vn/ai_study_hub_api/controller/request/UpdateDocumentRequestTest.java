package vn.ai_study_hub_api.controller.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class UpdateDocumentRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDeserializeTagsAsArray() throws Exception {
        String json = "{\"tags\":[1, 2]}";
        UpdateDocumentRequest request = objectMapper.readValue(json, UpdateDocumentRequest.class);
        
        assertNotNull(request.getTags());
        assertEquals(2, request.getTags().size());
        assertEquals(1, request.getTags().get(0));
        assertEquals(2, request.getTags().get(1));
    }

    @Test
    public void testDeserializeTagsAsString() throws Exception {
        String json = "{\"tags\":\"1, 2, 3\"}";
        UpdateDocumentRequest request = objectMapper.readValue(json, UpdateDocumentRequest.class);
        
        assertNotNull(request.getTags());
        assertEquals(3, request.getTags().size());
        assertEquals(1, request.getTags().get(0));
        assertEquals(2, request.getTags().get(1));
        assertEquals(3, request.getTags().get(2));
    }

    @Test
    public void testDeserializeTagsAsEmptyString() throws Exception {
        String json = "{\"tags\":\"\"}";
        UpdateDocumentRequest request = objectMapper.readValue(json, UpdateDocumentRequest.class);
        
        assertNotNull(request.getTags());
        assertTrue(request.getTags().isEmpty());
    }

    @Test
    public void testDeserializeTagsAsNull() throws Exception {
        String json = "{\"tags\":null}";
        UpdateDocumentRequest request = objectMapper.readValue(json, UpdateDocumentRequest.class);
        
        assertNull(request.getTags());
    }
}
