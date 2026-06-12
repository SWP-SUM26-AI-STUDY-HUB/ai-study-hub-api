package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.repository.TagRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TagServiceImplTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagServiceImpl tagService;

    @Test
    void searchTags_Success() {
        TagEntity tag1 = TagEntity.builder().id(1).label("Java").build();
        TagEntity tag2 = TagEntity.builder().id(2).label("JavaScript").build();

        when(tagRepository.findByLabelContainingIgnoreCase("Java")).thenReturn(List.of(tag1, tag2));

        List<TagResponse> results = tagService.searchTags("Java");

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Java", results.get(0).getLabel());
        assertEquals("JavaScript", results.get(1).getLabel());
        verify(tagRepository, times(1)).findByLabelContainingIgnoreCase("Java");
    }

    @Test
    void searchTags_EmptyKeyword_ReturnsEmptyList() {
        List<TagResponse> resultsNull = tagService.searchTags(null);
        List<TagResponse> resultsEmpty = tagService.searchTags("   ");

        assertNotNull(resultsNull);
        assertTrue(resultsNull.isEmpty());
        assertNotNull(resultsEmpty);
        assertTrue(resultsEmpty.isEmpty());

        verify(tagRepository, never()).findByLabelContainingIgnoreCase(anyString());
    }
}
