package gr.ntua.multimedia.persistence;

import gr.ntua.multimedia.persistence.dto.CategoryDTO;
import gr.ntua.multimedia.persistence.dto.DocumentDTO;
import gr.ntua.multimedia.persistence.dto.UserDTO;

import java.util.List;

public record DataStore(List<UserDTO> users, List<CategoryDTO> categories, List<DocumentDTO> documents) {}