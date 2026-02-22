package gr.ntua.multimedia.service;

final class RemovedDocInfo {
    final String title;
    final String categoryName;

    RemovedDocInfo(String title, String categoryName) {
        this.title = title;
        this.categoryName = categoryName;
    }

    String asLine() {
        return title + " | " + categoryName;
    }
}
