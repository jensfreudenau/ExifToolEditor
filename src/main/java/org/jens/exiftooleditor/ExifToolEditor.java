package org.jens.exiftooleditor;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * jpackage \
 * --name ExifToolEditor \
 * --input target \
 * --main-jar /Users/jensfreudenau/Development/java/ExifToolEditor/out/artifacts/ExifToolEditor_jar/ExifToolEditor.jar \
 * --main-class org.jens.exiftooleditor.ExifToolEditor \
 * --icon src/main/resources/icon.icns \
 * --java-options "-Xmx1024m" \
 * --runtime-image $JAVA_HOME \
 * --app-version 1.2.2
 */
public class ExifToolEditor extends Application {
    private static final String EXIFTOOL_PATH = "/opt/homebrew/bin/exiftool";
    private static final File DEFAULT_IMAGE_DIR = new File("/Users/jensfreudenau/Pictures");
    // Im resources-Ordner
    private static final String CATEGORIES_FILE_PATH = "categories.txt";
    private static final String WEBSITES_FILE_PATH = "websites.txt";
    private File[] selectedImages;

    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final ComboBox<String> websiteBox = new ComboBox<>();
    private final CheckBox multilineKeywords = new CheckBox("Ein Schlagwort pro Zeile");
    private final TextArea keywordsArea = new TextArea();
    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final Label imageLabel = new Label("Kein Bild ausgewählt");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        List<String> categories = loadFromTextFile(CATEGORIES_FILE_PATH);
        if (!categories.isEmpty()) {
            categoryBox.getItems().addAll(categories);
        } else {
            System.err.println("Warnung: Kategorien-Datei konnte nicht gelesen werden oder ist leer.");
        }
        List<String> websites = loadFromTextFile(WEBSITES_FILE_PATH);
        if (!websites.isEmpty()) {
            websiteBox.getItems().addAll(websites);
        } else {
            System.err.println("Warnung: Kategorien-Datei konnte nicht gelesen werden oder ist leer.");
        }
        GridPane form = new GridPane();
        form.setPadding(new Insets(10));
        form.setHgap(10);
        form.setVgap(10);
        int row = 0;
        addField(form, row++, "Kategorie:", categoryBox, 128);
        addField(form, row++, "Websites:", websiteBox, 128);
//        addField(form, row++, "Überschrift:", titleField, 64);//64
        addField(form, row++, "Überschrift:", titleField, 64);
        addArea(form, row++, "Beschreibung:", descriptionArea);
        addArea(form, row++, "Schlagwörter:", keywordsArea);
        form.add(multilineKeywords, 1, row++);

        Button loadButton = new Button("Bild laden");
        Button saveButton = new Button("Metadaten speichern");
        Button showExifDataButton = new Button("EXIF-Daten anzeigen");

        HBox buttonBox = new HBox(10, loadButton, saveButton, showExifDataButton, imageLabel);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10));

        VBox root = new VBox(form, buttonBox);
        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Exif Tool Keyword Editor  V1.1");
        stage.setScene(scene);
        stage.show();

        loadButton.setOnAction(e -> loadImage(stage));
        showExifDataButton.setOnAction(e -> showExifDataWindow());
        saveButton.setOnAction(e -> saveMetadata());
    }

    private List<String> loadFromTextFile(String path) {
        List<String> list = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is, "Website-Datei nicht als Ressource gefunden!")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    list.add(line);
                }
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Fehler beim Laden der Websites aus der Ressource: " + path + "\n" + e.getMessage());
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        list.add(line);
                    }
                }
            } catch (IOException ex) {
                System.err.println("Fehler beim Laden der Websites aus dem Dateisystem: " + path + "\n" + ex.getMessage());
                showError("Websites konnten nicht geladen werden.\n" + ex.getMessage());
            }
        }
        return list;
    }

    private void showExifDataWindow() {
        if (selectedImages == null || selectedImages.length == 0) {
            showError("Bitte zuerst ein Bild auswählen, um EXIF-Daten anzuzeigen.");
            return;
        }
        // Wir zeigen die EXIF-Daten nur für das ERSTE ausgewählte Bild an,
        // da die Anzeige für mehrere Bilder unübersichtlich werden könnte.
        File imageFile = selectedImages[0];
        Stage exifStage = new Stage();
        exifStage.setTitle("EXIF-Daten für: " + imageFile.getName());

        TextArea exifTextArea = new TextArea();
        exifTextArea.setEditable(false); // Die Daten sollen nicht editierbar sein
        exifTextArea.setWrapText(true);
        exifTextArea.setPrefRowCount(20); // Mehr Zeilen für die Anzeige

        ScrollPane scrollPane = new ScrollPane(exifTextArea); // Um Scrollen bei vielen Daten zu ermöglichen
        scrollPane.setFitToWidth(true); // Passt die Breite des ScrollPane an den Inhalt an

        VBox root = new VBox(scrollPane);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 600, 700); // Angepasste Größe für EXIF-Daten
        exifStage.setScene(scene);
        exifStage.show();

        // EXIF-Daten in einem Hintergrund-Thread lesen, um die UI nicht zu blockieren
        new Thread(() -> {
            try {
                // Befehl, um alle EXIF-Daten zu lesen (Standard-Ausgabe von ExifTool)
                // -json gibt die Daten im JSON-Format aus, was später leichter zu parsen wäre
                // für eine strukturiertere Anzeige. Für eine einfache Textanzeige ist -a -u -g1 -n gut.
                // -a : Duplikate zulassen
                // -u : Unbekannte Tags anzeigen
                // -g1: Gruppennamen anzeigen (z.B. [EXIF], [IPTC])
                // -n : Numerische Werte anzeigen (statt formatierten)
                ProcessBuilder builder = new ProcessBuilder(EXIFTOOL_PATH, "-a", "-u", "-g1", "-n", imageFile.getAbsolutePath());
                Process process = builder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                StringBuilder exifOutput = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    exifOutput.append(line).append("\n");
                }
                process.waitFor(); // Warten, bis der Prozess beendet ist

                // UI-Update muss im JavaFX Application Thread erfolgen
                javafx.application.Platform.runLater(() -> {
                    exifTextArea.setText(exifOutput.toString());
                });

            } catch (IOException | InterruptedException ex) {
                javafx.application.Platform.runLater(() -> {
                    exifTextArea.setText("Fehler beim Laden der EXIF-Daten:\n" + ex.getMessage());
                });
                ex.printStackTrace();
            }
        }).start();
    }

    private void addField(GridPane pane, int row, String label, Control field, int maxLength) {
        pane.add(new Label(label), 0, row);
        pane.add(field, 1, row);

        if (field instanceof TextField textField) {
            textField.setPrefWidth(600);

            if (maxLength > 0) { // Only apply if a positive maxLength is provided
                textField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue.length() > maxLength) {
                        textField.setText(oldValue); // Revert to the old value if limit exceeded
                    }
                });
            }
        }
    }

    private void addArea(GridPane pane, int row, String label, TextArea area) {
        area.setWrapText(true);
        area.setPrefRowCount(4);
        pane.add(new Label(label), 0, row);
        pane.add(area, 1, row);
    }

    private void loadImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Bild auswählen");
        fileChooser.setInitialDirectory(DEFAULT_IMAGE_DIR);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bilder", "*.jpg", "*.jpeg", "*.tif", "*.tiff"));
        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            selectedImages = files.toArray(new File[0]);
            imageLabel.setText(selectedImages.length + " Bilder ausgewählt");
            loadMetadataFromFile(selectedImages[0]);
        }
    }

    private void loadMetadataFromFile(File image) {
        try {
            String categoryValue = readTag(image, List.of("IPTC:Category", "XMP:Category"));
            categoryBox.getItems().stream()
                    .filter(item -> item.startsWith(categoryValue))
                    .findFirst().ifPresent(categoryBox::setValue);

            String keywordsRaw = readTag(image, List.of("IPTC:Keywords", "XMP-dc:Subject"));
            String keywordsReplacedBlank = keywordsRaw.replace(" ", "_");
            String keywordsReplacedUpperCase = keywordsReplacedBlank.replace(",_", ",");
            String keywordsReplacedRaw = keywordsReplacedUpperCase.toLowerCase();
            if (multilineKeywords.isSelected()) {
                keywordsArea.setText(keywordsReplacedRaw.replace(", ", "\n"));
            } else {
                keywordsArea.setText(keywordsReplacedRaw);
            }
            titleField.setText(readTag(image, List.of("IPTC:ObjectName", "XMP-dc:Title")));
            descriptionArea.setText(readTag(image, List.of("IPTC:Caption-Abstract", "XMP-dc:Description")));
            String websiteValue = readTag(image, List.of("IPTC:Creator Work URL", "XMP:Creator Work URL"));
            String domainName = websiteValue.replaceAll("http(s)?://|www\\.|/.*", "");
            websiteBox.getItems().stream()
                    .filter(item -> item.startsWith(domainName))
                    .findFirst().ifPresent(websiteBox::setValue);
        } catch (Exception ex) {
            showError("Fehler beim Lesen:\n" + ex.getMessage());
        }
    }

    private void saveMetadata() {
        if (selectedImages == null || selectedImages.length == 0) {
            showError("Bitte zuerst ein Bild laden.");
            return;
        }
        try {
            String selectedCat = categoryBox.getValue().split(" - ")[0];
            String selectedWebsite = websiteBox.getValue().split(" - ")[0];
            String[] keywords = multilineKeywords.isSelected()
                    ? keywordsArea.getText().split("\\r?\\n")
                    : keywordsArea.getText().split(",");

            for (File imageFile : selectedImages) {
                List<String> command = new ArrayList<>();
                command.add(EXIFTOOL_PATH);
                command.add("-overwrite_original");
                command.add("-IPTC:Keywords=");
                command.add("-XMP-dc:Subject=");
                for (String kw : keywords) {
                    kw = kw.trim();
                    if (!kw.isEmpty()) {
                        command.add("-IPTC:Keywords=" + kw);
                        command.add("-XMP-dc:Subject=" + kw);
                        command.add("-overwrite_original");
                    }
                }
                command.add("-IPTC:ObjectName=" + titleField.getText().trim());
                command.add("-XMP-dc:Title=" + titleField.getText().trim());
                command.add("-IPTC:Caption-Abstract=" + descriptionArea.getText().trim());
                command.add("-XMP-dc:Description=" + descriptionArea.getText().trim());
                command.add("-IPTC:Category=" + selectedCat);
                command.add("-XMP:Category=" + selectedCat);
                command.add("-XMP-dc:Subject+=" + selectedCat);
                command.add("-IPTC:Source=" + selectedWebsite);
                command.add("-XMP-dc:Source=" + selectedWebsite);
                command.add("-XMP:CreatorWorkURL=" + selectedWebsite);
                command.add(imageFile.getAbsolutePath());

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();
                process.waitFor();
            }

            showInfo(selectedImages.length + " Bilddateien wurden aktualisiert.");

        } catch (Exception ex) {
            showError("Fehler beim Schreiben:\n" + ex.getMessage());
        }
    }

    private String readTag(File imageFile, List<String> possibleTags) throws IOException {
        for (String tag : possibleTags) {
            ProcessBuilder builder = new ProcessBuilder(EXIFTOOL_PATH, "-" + tag, imageFile.getAbsolutePath());
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts[0].trim().equalsIgnoreCase(tag.split(":")[1])) {
                        return parts[1].trim();
                    }
                }
            }
        }
        return "";
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Fehler");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle("Fertig");
        alert.showAndWait();
    }
}
