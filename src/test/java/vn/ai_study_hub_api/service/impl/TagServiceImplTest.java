package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TagServiceImplTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TagServiceImpl tagService;

    @Test
    void searchTags_Success() {
        TagEntity tag1 = TagEntity.builder().id(1).label("Java").build();
        TagEntity tag2 = TagEntity.builder().id(2).label("JavaScript").build();
        UUID userId = UUID.randomUUID();

        when(tagRepository.searchTagsForUser("Java", userId)).thenReturn(List.of(tag1, tag2));

        List<TagResponse> results = tagService.searchTags("Java", userId);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Java", results.get(0).getLabel());
        assertEquals("JavaScript", results.get(1).getLabel());
        verify(tagRepository, times(1)).searchTagsForUser("Java", userId);
    }

    @Test
    void searchTags_EmptyKeyword_ReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        List<TagResponse> resultsNull = tagService.searchTags(null, userId);
        List<TagResponse> resultsEmpty = tagService.searchTags("   ", userId);

        assertNotNull(resultsNull);
        assertTrue(resultsNull.isEmpty());
        assertNotNull(resultsEmpty);
        assertTrue(resultsEmpty.isEmpty());

        verify(tagRepository, never()).searchTagsForUser(anyString(), any());
    }

    @Test
    void createUserTags_Success() {
        UUID userId = UUID.randomUUID();
        UserEntity mockUser = UserEntity.builder().id(userId).build();
        List<String> inputTags = List.of("Math", "Physics", " ");
        TagEntity mathEntity = TagEntity.builder().id(1).label("Math").build();

        when(userRepository.getReferenceById(userId)).thenReturn(mockUser);
        when(tagRepository.findByLabelIgnoreCaseAndVisibility("Math", vn.ai_study_hub_api.model.TagVisibility.PUBLIC))
                .thenReturn(java.util.Optional.of(mathEntity));
        when(tagRepository.findByLabelIgnoreCaseAndVisibility("Physics", vn.ai_study_hub_api.model.TagVisibility.PUBLIC))
                .thenReturn(java.util.Optional.empty());
        when(tagRepository.findByLabelIgnoreCaseAndVisibilityAndCreatedBy_Id("Physics", vn.ai_study_hub_api.model.TagVisibility.PRIVATE, userId))
                .thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(invocation -> {
            TagEntity argument = invocation.getArgument(0);
            return TagEntity.builder().id(2).label(argument.getLabel()).build();
        });

        List<TagResponse> results = tagService.createUserTags(inputTags, userId);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Math", results.get(0).getLabel());
        assertEquals("Physics", results.get(1).getLabel());
        verify(tagRepository, times(1)).save(any(TagEntity.class));
    }

    @Test
    void createUserTags_Failure_TagLengthExceedsLimit() {
        UUID userId = UUID.randomUUID();
        UserEntity mockUser = UserEntity.builder().id(userId).build();
        when(userRepository.getReferenceById(userId)).thenReturn(mockUser);
        List<String> inputTags = List.of("ThisTagLabelIsWayTooLongAndExceedsThirtyCharactersLimit");

        assertThrows(vn.ai_study_hub_api.exception.AppException.class, () -> {
            tagService.createUserTags(inputTags, userId);
        });

        verify(tagRepository, never()).save(any(TagEntity.class));
    }
}

