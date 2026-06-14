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

    @Test
    void createTags_Success() {
        List<String> inputTags = List.of("Math", "Physics", " ");
        TagEntity mathEntity = TagEntity.builder().id(1).label("Math").build();
        TagEntity physicsEntity = TagEntity.builder().id(2).label("Physics").build();

        when(tagRepository.findByLabel("Math")).thenReturn(java.util.Optional.of(mathEntity));
        when(tagRepository.findByLabel("Physics")).thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(invocation -> {
            TagEntity argument = invocation.getArgument(0);
            return TagEntity.builder().id(2).label(argument.getLabel()).build();
        });

        List<TagResponse> results = tagService.createTags(inputTags);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Math", results.get(0).getLabel());
        assertEquals("Physics", results.get(1).getLabel());
        verify(tagRepository, times(1)).findByLabel("Math");
        verify(tagRepository, times(1)).findByLabel("Physics");
        verify(tagRepository, times(1)).save(any(TagEntity.class));
    }

    @Test
    void createTags_Failure_TagLengthExceedsLimit() {
        List<String> inputTags = List.of("ThisTagLabelIsWayTooLongAndExceedsThirtyCharactersLimit");

        assertThrows(vn.ai_study_hub_api.exception.AppException.class, () -> {
            tagService.createTags(inputTags);
        });

        verify(tagRepository, never()).findByLabel(anyString());
        verify(tagRepository, never()).save(any(TagEntity.class));
    }
}
