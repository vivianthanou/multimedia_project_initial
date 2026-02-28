# MediaLab Documents (JavaFX Document Management System)

Semester project for “Τεχνολογία Πολυμέσων” – NTUA.  
JavaFX desktop application for managing users, categories, documents (with versioning) and follow/notifications.

---

## Requirements / Environment

- **Java (JDK):** 25  
  (Project SDK in IntelliJ: `java 25`)

- **JavaFX SDK:** 25.0.2  
  The application uses JavaFX modules.

---

## How to Run (IntelliJ IDEA)

### 1) Open the project
- `File → Open…` and select the project folder.

### 2) Set Project SDK
- `File → Project Structure → Project`
- Set **Project SDK** to **JDK 25**.

### 3) Configure JavaFX (VM options)
Open:
- `Run → Edit Configurations…`
- Select configuration **Main** (main class: `gr.ntua.multimedia.Main`)
- In **VM options** paste the path you have saved the javafx, for example: 

```text
--module-path "C:\Users\Downloads\openjfx-25.0.2_windows-x64_bin-sdk\javafx-sdk-25.0.2\lib" --add-modules javafx.controls,javafx.fxml