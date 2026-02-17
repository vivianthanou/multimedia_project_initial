package gr.ntua.multimedia.service;

import gr.ntua.multimedia.domain.Admin;
import gr.ntua.multimedia.domain.Category;
import gr.ntua.multimedia.domain.Document;
import gr.ntua.multimedia.domain.User;
import gr.ntua.multimedia.util.IdUtil;
import gr.ntua.multimedia.util.ValidationUtil;

import java.util.List;
import java.util.Map;

final class CategoryService {
    private final Map<String, User> usersByUsername;
    private final Map<String, Category> categoriesById;
    private final Map<String, Document> documentsById;

    CategoryService(Map<String, User> usersByUsername,
                    Map<String, Category> categoriesById,
                    Map<String, Document> documentsById) {
        this.usersByUsername = usersByUsername;
        this.categoriesById = categoriesById;
        this.documentsById = documentsById;
    }

    Category addCategory(Admin adminActor, String categoryName) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        ValidationUtil.requireNonBlank(categoryName, "categoryName");
        Category category = new Category(IdUtil.newId(), categoryName);
        categoriesById.put(category.getId(), category);
        return category;
    }

    void renameCategory(Admin adminActor, String categoryId, String newName) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        Category category = AccessControl.findCategory(categoryId, categoriesById);
        category.rename(newName);
    }

    void deleteCategory(Admin adminActor, String categoryId) {
        AccessControl.requireAdmin(adminActor, usersByUsername);
        Category category = AccessControl.findCategory(categoryId, categoriesById);
        List<String> toDelete = documentsById.values().stream()
                .filter(d -> d.getCategoryId().equals(category.getId()))
                .map(Document::getId)
                .toList();

        for (String docId : toDelete) {
            documentsById.remove(docId);
            for (User user : usersByUsername.values()) {
                user.unfollowDocument(docId);
            }
        }
        categoriesById.remove(categoryId);
    }
}